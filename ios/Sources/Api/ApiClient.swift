import Foundation

enum ApiError: Error, Sendable {
    case problem(ApiProblem)
    case transport(String)
    case notConfigured
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

    private func send<T: Decodable & Sendable>(
        path: String,
        method: String,
        body: Data?,
        as type: T.Type
    ) async throws -> T {
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

        return try Self.decoder.decode(type, from: data)
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
