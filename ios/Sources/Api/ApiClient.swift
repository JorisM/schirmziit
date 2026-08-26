import Foundation

enum ApiError: Error, Sendable {
    case problem(ApiProblem)
    case transport(String)
    case notConfigured
}

/// The result of a write the parent triggered — adding a child, removing one,
/// disconnecting a phone.
///
/// A write cannot be handled the way a read is: `fetchUsage` has a value to
/// fall back on, while a failed delete has nothing to render and everything to
/// explain. `failed` carries the server's own sentence so the screen can say
/// what happened rather than "something went wrong".
enum WriteOutcome: Equatable, Sendable {
    case ok
    case failed(String)
}

extension WriteOutcome {
    /// Every parent-triggered write reports a failure the same way: the
    /// server's own sentence when there is one, the offline line when the
    /// request never landed. Written once so a new write cannot quietly swallow
    /// its error by forgetting a `catch`.
    static func of(_ body: @Sendable () async throws -> Void) async -> WriteOutcome {
        do {
            try await body()
            return .ok
        } catch let ApiError.problem(problem) {
            return .failed(problem.detail)
        } catch {
            return .failed(S("error.offline"))
        }
    }
}

/// Talks to one family's server. The base URL is whatever the parent typed —
/// there is no hardcoded backend, same as the Android agent.
actor ApiClient {
    private let session: URLSession
    private var baseURL: URL?

    init(session: URLSession = .shared) {
        self.session = session
    }

    func configure(baseURL: URL?) {
        self.baseURL = baseURL
    }

    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        // The server sends RFC3339 with fractional seconds on some fields and
        // without on others; try both rather than failing the whole response.
        decoder.dateDecodingStrategy = .custom { container in
            let text = try container.singleValueContainer().decode(String.self)
            if let date = ISO8601DateFormatter.withFractional.date(from: text) { return date }
            if let date = ISO8601DateFormatter.plain.date(from: text) { return date }
            throw DecodingError.dataCorrupted(
                .init(codingPath: container.codingPath, debugDescription: "unrecognised date: \(text)")
            )
        }
        return decoder
    }()

    func get<T: Decodable & Sendable>(_ path: String, as type: T.Type) async throws -> T {
        try await send(path: path, method: "GET", body: nil, as: type)
    }

    func post<T: Decodable & Sendable>(
        _ path: String,
        body: [String: String],
        as type: T.Type
    ) async throws -> T {
        try await send(path: path, method: "POST", body: try JSONEncoder().encode(body), as: type)
    }

    /// The server answers a delete with 204 and no body, so there is nothing to
    /// decode — a `Decodable` variant would throw on success.
    func delete(_ path: String) async throws {
        _ = try await raw(path: path, method: "DELETE", body: nil)
    }

    private func send<T: Decodable & Sendable>(
        path: String,
        method: String,
        body: Data?,
        as type: T.Type
    ) async throws -> T {
        try Self.decoder.decode(type, from: try await raw(path: path, method: method, body: body))
    }

    private func raw(path: String, method: String, body: Data?) async throws -> Data {
        guard let baseURL, let url = URL(string: path, relativeTo: baseURL) else {
            throw ApiError.notConfigured
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        if body != nil {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw ApiError.transport(error.localizedDescription)
        }

        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            if let problem = try? Self.decoder.decode(ApiProblem.self, from: data) {
                throw ApiError.problem(problem)
            }
            throw ApiError.transport("HTTP \(status)")
        }

        return data
    }
}

extension ISO8601DateFormatter {
    static let withFractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    static let plain = ISO8601DateFormatter()
}
