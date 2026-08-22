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

/// The two endpoints a child device is allowed to touch. Both are write-only by
/// design on the server side, so a stolen device token cannot read the family's
/// history.
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
