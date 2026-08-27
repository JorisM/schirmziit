import XCTest
@testable import SchirmziitKit

/// The read endpoints are not part of `core::wire`, so these models are
/// hand-written — which means they can drift from the server. These fixtures are
/// captured from the real API; if the server's shape changes, decoding fails
/// here rather than showing an empty screen on someone's phone.
final class ContractTests: XCTestCase {
    private func decode<T: Decodable>(_ json: String, as type: T.Type) throws -> T {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        decoder.dateDecodingStrategy = .custom { container in
            let text = try container.singleValueContainer().decode(String.self)
            if let date = ISO8601DateFormatter.withFractional.date(from: text) { return date }
            if let date = ISO8601DateFormatter.plain.date(from: text) { return date }
            throw DecodingError.dataCorrupted(
                .init(codingPath: container.codingPath, debugDescription: text)
            )
        }
        return try decoder.decode(type, from: Data(json.utf8))
    }

    func testDecodesChildrenList() throws {
        let children = try decode(
            #"[{"id":"c8a19dc2-892d-4895-a82f-a80633152679","display_name":"Fairphone kid","today_ms":0}]"#,
            as: [ChildResponse].self
        )
        XCTAssertEqual(children.first?.displayName, "Fairphone kid")
    }

    func testDecodesUsageResponseAsTheServerSendsIt() throws {
        let json = #"""
        {
          "child_id": "c8a19dc2-892d-4895-a82f-a80633152679",
          "from": "2026-08-22", "to": "2026-08-22", "bucket": "hour", "tz": "Europe/Zurich",
          "devices": [
            {"id":"4cd80674","label":"FP4","last_seen_at":"2026-08-22T07:00:26.794018Z","stale":false},
            {"id":"b4f26d17","label":"Old phone","last_seen_at":null,"stale":true}
          ],
          "series": [
            {"package":"com.zhiliaoapp.musically","label":"TikTok",
             "points":[{"start":"2026-08-22T08:00:00+02:00","foreground_ms":1200000,"launch_count":9}]}
          ],
          "device_totals": [
            {"start":"2026-08-22T08:00:00+02:00","screen_on_ms":1800000,"unlock_count":7}
          ]
        }
        """#
        let usage = try decode(json, as: UsageResponse.self)

        XCTAssertEqual(usage.tz, "Europe/Zurich")
        XCTAssertEqual(usage.series.first?.label, "TikTok")
        XCTAssertEqual(usage.screenTimeMs, 1_200_000)
        XCTAssertEqual(usage.unlocks, 7)
        // Fractional seconds on one device, null on the other: both must decode.
        XCTAssertNotNil(usage.devices.first?.lastSeenAt)
        XCTAssertNil(usage.devices.last?.lastSeenAt)
        XCTAssertTrue(usage.devices.last?.stale == true)
    }

    func testDecodesProblemJson() throws {
        let problem = try decode(
            #"{"type":"https://schirmziit.ch/problems/not-found","title":"not-found","status":404,"detail":"not found","code":"SZ-E201","ref":"7f3a9c"}"#,
            as: ApiProblem.self
        )
        XCTAssertEqual(problem.status, 404)
        XCTAssertEqual(problem.detail, "not found")
        XCTAssertEqual(problem.code, "SZ-E201")
        XCTAssertEqual(problem.ref, "7f3a9c")
    }

    /// A self-hoster upgrades their server when they get round to it, so an app
    /// newer than the server is normal. This body is what a server from before
    /// the catalog sends; refusing to decode it would report a healthy old
    /// server as a captive portal.
    func testDecodesAProblemFromAServerOlderThanTheCatalog() throws {
        let problem = try decode(
            #"{"type":"https://schirmziit.ch/problems/not-found","title":"not-found","status":404,"detail":"not found"}"#,
            as: ApiProblem.self
        )
        XCTAssertNil(problem.code)
        XCTAssertNil(problem.ref)

        let error = AppError(problem: problem, endpoint: "/v1/children")
        XCTAssertEqual(error.code.wire, "SZ-E901")
        XCTAssertEqual(error.ref.count, 6, "a local reference stands in for the one the server never sent")
    }

    func testDecodesMe() throws {
        let me = try decode(
            #"{"id":"p1","email":"joris@example.ch","family_id":"f1"}"#,
            as: MeResponse.self
        )
        XCTAssertEqual(me.familyId, "f1")
    }
}
