import Foundation

public struct SetupChild: Codable, Equatable, Sendable, Identifiable {
    public let id: String
    public let displayName: String

    enum CodingKeys: String, CodingKey {
        case id
        case displayName = "display_name"
    }
}

enum ChildSetupError: Error, Equatable {
    case unauthorized
    case http(Int)
    case malformedResponse
}

/// Turning a parent session into a child device.
///
/// The sequence matters more than the code: claim a device token, save it, and
/// only then end the parent session — so a failure never leaves a child's phone
/// holding a parent session, and a success never leaves it holding one either.
struct ChildSetup: Sendable {
    let baseURL: URL
    let transport: Transport
    /// The `schirmziit_session=…` cookie captured when the parent signed in.
    let sessionCookie: String

    private var authHeaders: [String: String] {
        ["cookie": sessionCookie, "content-type": "application/json"]
    }

    func children() async throws -> [SetupChild] {
        let response = try await transport.send(
            HttpRequest(
                url: baseURL.appendingPathComponent("v1/children"),
                method: "GET",
                headers: ["cookie": sessionCookie]
            )
        )
        switch response.status {
        case 200...299:
            guard let children = try? JSONDecoder().decode([SetupChild].self, from: response.body) else {
                throw ChildSetupError.malformedResponse
            }
            return children
        case 401, 403:
            throw ChildSetupError.unauthorized
        case let status:
            throw ChildSetupError.http(status)
        }
    }

    func claim(childId: String, platform: String, model: String, label: String) async throws -> Enrolled {
        let body = try JSONEncoder().encode(
            ClaimBody(platform: platform, model: model, label: label)
        )
        let response = try await transport.send(
            HttpRequest(
                url: baseURL.appendingPathComponent("v1/children/\(childId)/devices"),
                method: "POST",
                headers: authHeaders,
                body: body
            )
        )
        switch response.status {
        case 200...299:
            guard let decoded = try? JSONDecoder().decode(ClaimResponse.self, from: response.body) else {
                throw ChildSetupError.malformedResponse
            }
            return Enrolled(deviceId: decoded.device_id, token: decoded.token)
        case 401, 403:
            throw ChildSetupError.unauthorized
        case let status:
            throw ChildSetupError.http(status)
        }
    }

    /// Best effort: the device token is already saved, and a session left alive
    /// on a child's phone is the thing worth avoiding — but if this call fails
    /// the session still expires on its own, so it must not fail the setup.
    func endSession() async {
        _ = try? await transport.send(
            HttpRequest(
                url: baseURL.appendingPathComponent("v1/auth/logout"),
                method: "POST",
                headers: ["cookie": sessionCookie]
            )
        )
    }

    /// Signs a parent in for the length of a setup (or of an unlock) and returns
    /// the session cookie. The cookie is held in memory only: a child's phone
    /// must not persist one.
    static func signIn(
        baseURL: URL,
        transport: Transport,
        email: String,
        password: String
    ) async -> String? {
        let body = try? JSONEncoder().encode(["email": email, "password": password])
        guard let body else { return nil }
        let response = try? await transport.send(
            HttpRequest(
                url: baseURL.appendingPathComponent("v1/auth/login"),
                method: "POST",
                headers: ["content-type": "application/json"],
                body: body
            )
        )
        guard let response, (200..<300).contains(response.status) else { return nil }
        return response.setCookie
    }
}

private struct ClaimBody: Encodable {
    let platform: String
    let model: String
    let label: String
}

// Snake case on purpose: mirrors the server's serde field names.
private struct ClaimResponse: Decodable {
    let device_id: String
    let token: String
}
