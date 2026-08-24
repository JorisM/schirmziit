import Foundation

struct Enrolled: Equatable, Sendable {
    var deviceId: String
    var token: String
}

enum AgentClientError: Error, Equatable {
    /// The code was wrong, already used, or expired.
    case unknownCode
    case unauthorized
    case http(Int)
    case malformedResponse
}

/// The endpoints a child device is allowed to touch: two write-only ones
/// (`enroll`, `ingest`), and one read that hands back only this same device's
/// own child — never another child's history, and never anyone else's phone.
struct AgentClient: Sendable {
    let baseURL: URL
    let transport: Transport

    func enroll(code: String, platform: String, model: String, label: String) async throws -> Enrolled {
        let body = try JSONEncoder().encode(
            EnrollRequestBody(code: code.uppercased(), platform: platform, model: model, label: label)
        )
        let response = try await transport.send(
            HttpRequest(
                url: baseURL.appendingPathComponent("v1/enroll"),
                method: "POST",
                headers: ["content-type": "application/json"],
                body: body
            )
        )

        switch response.status {
        case 200...299:
            guard let decoded = try? JSONDecoder().decode(EnrollResponseBody.self, from: response.body) else {
                throw AgentClientError.malformedResponse
            }
            return Enrolled(deviceId: decoded.device_id, token: decoded.token)
        case 404:
            throw AgentClientError.unknownCode
        case let status:
            throw AgentClientError.http(status)
        }
    }

    /// Returns the raw response body. The core parses it — the agent must not
    /// second-guess which hours were accepted.
    func ingest(token: String, body: String) async throws -> String {
        let response = try await transport.send(
            HttpRequest(
                url: baseURL.appendingPathComponent("v1/ingest"),
                method: "POST",
                headers: [
                    "content-type": "application/json",
                    "authorization": "Bearer \(token)",
                ],
                body: Data(body.utf8)
            )
        )

        switch response.status {
        case 200...299:
            guard let text = String(data: response.body, encoding: .utf8) else {
                throw AgentClientError.malformedResponse
            }
            return text
        case 401, 403:
            throw AgentClientError.unauthorized
        case let status:
            throw AgentClientError.http(status)
        }
    }

    /// The one read a device token buys: this phone's own child, no id in the
    /// path. Returns the raw body — the core parses it, so both agents agree on
    /// what a day means.
    func myUsage(token: String, from: String, to: String, bucket: String, tz: String) async throws -> String {
        var components = URLComponents(
            url: baseURL.appendingPathComponent("v1/me/usage"), resolvingAgainstBaseURL: false
        )
        components?.queryItems = [
            URLQueryItem(name: "from", value: from),
            URLQueryItem(name: "to", value: to),
            URLQueryItem(name: "bucket", value: bucket),
            URLQueryItem(name: "tz", value: tz),
        ]
        guard let url = components?.url else { throw AgentClientError.malformedResponse }

        let response = try await transport.send(
            HttpRequest(url: url, method: "GET", headers: ["authorization": "Bearer \(token)"], body: nil)
        )

        switch response.status {
        case 200...299:
            guard let text = String(data: response.body, encoding: .utf8) else {
                throw AgentClientError.malformedResponse
            }
            return text
        case 401, 403:
            throw AgentClientError.unauthorized
        case let status:
            throw AgentClientError.http(status)
        }
    }
}

// Snake case on purpose: these two mirror the server's serde field names.
private struct EnrollRequestBody: Encodable {
    let code: String
    let platform: String
    let model: String
    let label: String
}

private struct EnrollResponseBody: Decodable {
    let device_id: String
    let token: String
}
