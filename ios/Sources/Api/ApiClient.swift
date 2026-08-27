import Foundation

/// The result of a write the parent triggered — adding a child, removing one,
/// disconnecting a phone.
///
/// A write cannot be handled the way a read is: `fetchUsage` has a value to
/// fall back on, while a failed delete has nothing to render and everything to
/// explain. `failed` carries the typed error, so the screen shows what happened
/// with its code and reference rather than "something went wrong".
enum WriteOutcome: Sendable, Equatable {
    case ok
    case failed(AppError)

    /// Compared by what failed, not by which occurrence: every `AppError`
    /// carries its own id and reference, so a synthesised `==` would report two
    /// identical failures as different and make the comparison useless.
    static func == (lhs: WriteOutcome, rhs: WriteOutcome) -> Bool {
        switch (lhs, rhs) {
        case (.ok, .ok): true
        case let (.failed(left), .failed(right)): left.code == right.code
        default: false
        }
    }
}

extension WriteOutcome {
    /// Every parent-triggered write reports a failure the same way. Written
    /// once so a new write cannot quietly swallow its error by forgetting a
    /// `catch`.
    static func of(_ body: @Sendable () async throws -> Void) async -> WriteOutcome {
        do {
            try await body()
            return .ok
        } catch let error as AppError {
            return .failed(error)
        } catch {
            return .failed(AppError.transport(error, endpoint: nil))
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
        let data = try await raw(path: path, method: method, body: body)
        do {
            return try Self.decoder.decode(type, from: data)
        } catch {
            // A 200 whose body is not what this app expects. Named, so the
            // screen can say "update the app" instead of shrugging.
            throw AppError.local(.localDecodeFailed, endpoint: path)
        }
    }

    private func raw(path: String, method: String, body: Data?) async throws -> Data {
        guard let baseURL, let url = URL(string: path, relativeTo: baseURL) else {
            throw AppError.local(.baseUrlNotConfigured, endpoint: path)
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
            throw AppError.transport(error, endpoint: path)
        }

        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            guard let problem = try? Self.decoder.decode(ApiProblem.self, from: data) else {
                // Not the API's problem shape: something answered in the
                // server's place — a guest-network login page, a proxy error
                // page. It must throw rather than be read as anything else.
                throw AppError.badResponseBody(endpoint: path, httpStatus: status)
            }
            throw AppError(problem: problem, endpoint: path)
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
