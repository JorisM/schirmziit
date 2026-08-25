import XCTest
@testable import SchirmziitKit

final class AgentClientTests: XCTestCase {
    private let base = URL(string: "https://schirmziit.example.ch")!

    func testEnrollPostsTheFieldsTheServerExpects() async throws {
        let transport = StubTransport(
            status: 201,
            body: #"{"device_id":"4cd80674-0000-0000-0000-000000000000","token":"tok"}"#
        )
        let client = AgentClient(baseURL: base, transport: transport)

        let enrolled = try await client.enroll(code: "abcd1234", platform: "ios", model: "iPhone15,3", label: "Emma")

        XCTAssertEqual(enrolled, Enrolled(deviceId: "4cd80674-0000-0000-0000-000000000000", token: "tok"))
        let request = try XCTUnwrap(transport.sent.first)
        XCTAssertEqual(request.url.absoluteString, "https://schirmziit.example.ch/v1/enroll")
        XCTAssertEqual(request.method, "POST")
        XCTAssertEqual(request.headers["content-type"], "application/json")

        let body = try XCTUnwrap(request.body)
        let json = try XCTUnwrap(try JSONSerialization.jsonObject(with: body) as? [String: String])
        XCTAssertEqual(json["code"], "ABCD1234", "codes are shown uppercase, so accept either case")
        XCTAssertEqual(json["platform"], "ios")
        XCTAssertEqual(json["model"], "iPhone15,3")
        XCTAssertEqual(json["label"], "Emma")
    }

    func testAnUnknownCodeIsItsOwnErrorSoTheUiCanExplainIt() async {
        let client = AgentClient(baseURL: base, transport: StubTransport(status: 404, body: ""))
        do {
            _ = try await client.enroll(code: "NOPE", platform: "ios", model: "m", label: "l")
            XCTFail("expected an error")
        } catch {
            XCTAssertEqual(error as? AgentClientError, .unknownCode)
        }
    }

    func testIngestSendsTheBearerTokenAndReturnsTheRawBody() async throws {
        let transport = StubTransport(status: 200, body: #"{"accepted":[],"rejected":[]}"#)
        let client = AgentClient(baseURL: base, transport: transport)

        let response = try await client.ingest(token: "tok", body: #"{"schema":1}"#)

        XCTAssertEqual(response, #"{"accepted":[],"rejected":[]}"#)
        let request = try XCTUnwrap(transport.sent.first)
        XCTAssertEqual(request.url.absoluteString, "https://schirmziit.example.ch/v1/ingest")
        XCTAssertEqual(request.headers["authorization"], "Bearer tok")
        XCTAssertEqual(request.body, Data(#"{"schema":1}"#.utf8))
    }

    func testARevokedDeviceIsReportedAsUnauthorized() async {
        let client = AgentClient(baseURL: base, transport: StubTransport(status: 401, body: ""))
        do {
            _ = try await client.ingest(token: "tok", body: "{}")
            XCTFail("expected an error")
        } catch {
            XCTAssertEqual(error as? AgentClientError, .unauthorized)
        }
    }

    func testMyUsageSendsTheDeviceTokenAndReturnsTheRawBody() async throws {
        let transport = StubTransport(
            status: 200,
            body: #"{"from":"2026-08-20","to":"2026-08-20","series":[],"device_totals":[]}"#
        )
        let client = AgentClient(baseURL: base, transport: transport)

        let body = try await client.myUsage(
            token: "t0ken", from: "2026-08-20", to: "2026-08-20", bucket: "day", tz: "Europe/Zurich"
        )

        XCTAssertTrue(body.contains("device_totals"), "the core parses it, not the agent")
        let request = try XCTUnwrap(transport.sent.first)
        XCTAssertEqual(request.headers["authorization"], "Bearer t0ken")
        XCTAssertTrue(request.url.absoluteString.contains("/v1/me/usage"))
        XCTAssertTrue(request.url.absoluteString.contains("bucket=day"))
    }

    func testMyUsageReportsAnExpiredTokenAsUnauthorized() async {
        let client = AgentClient(baseURL: base, transport: StubTransport(status: 401, body: ""))
        do {
            _ = try await client.myUsage(token: "old", from: "2026-08-20", to: "2026-08-20", bucket: "day", tz: "UTC")
            XCTFail("expected unauthorized")
        } catch {
            XCTAssertEqual(error as? AgentClientError, .unauthorized)
        }
    }
}
