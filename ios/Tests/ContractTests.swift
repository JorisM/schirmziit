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

    func testDecodesTheWeekComparisonAsTheServerSendsIt() throws {
        let json = #"""
        {
          "child_id": "c8a19dc2-892d-4895-a82f-a80633152679",
          "tz": "Europe/Zurich",
          "week": {
            "from": "2026-08-13", "to": "2026-08-19",
            "previous_from": "2026-08-06", "previous_to": "2026-08-12",
            "total_ms": 44400000, "previous_total_ms": 42000000,
            "evening_ms": 7200000, "previous_evening_ms": 4800000,
            "evening_from_hour": 21,
            "movers": [
              {"package":"com.zhiliaoapp.musically","label":"TikTok",
               "foreground_ms":9000000,"previous_foreground_ms":3600000}
            ],
            "previous_measured": true
          }
        }
        """#
        let insight = try decode(json, as: InsightResponse.self)

        XCTAssertEqual(insight.week.eveningFromHour, 21)
        XCTAssertEqual(insight.week.deltaMs, 2_400_000)
        XCTAssertEqual(insight.week.eveningDeltaMs, 2_400_000)
        XCTAssertEqual(insight.week.movers.first?.deltaMs, 5_400_000)
        XCTAssertTrue(insight.week.previousMeasured)
    }

    /// A first week is the state a new family is in for seven days, so it has to
    /// decode as readily as a full one.
    func testDecodesAWeekWithNothingBehindIt() throws {
        let json = #"""
        {
          "child_id": "c8a19dc2-892d-4895-a82f-a80633152679",
          "tz": "Europe/Zurich",
          "week": {
            "from": "2026-08-13", "to": "2026-08-19",
            "previous_from": "2026-08-06", "previous_to": "2026-08-12",
            "total_ms": 3600000, "previous_total_ms": 0,
            "evening_ms": 0, "previous_evening_ms": 0,
            "evening_from_hour": 21, "movers": [], "previous_measured": false
          }
        }
        """#
        let insight = try decode(json, as: InsightResponse.self)

        XCTAssertFalse(insight.week.previousMeasured)
        XCTAssertTrue(insight.week.movers.isEmpty)
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

    /// Captured from `POST /v1/children/{id}/enrollments`. `expires_at` arrives
    /// with fractional seconds here and without them elsewhere in the same API,
    /// which is why the decoder tries both formats.
    func testDecodesAnEnrollment() throws {
        let enrollment = try decode(
            #"""
            {"code":"A2B3C4","expires_at":"2026-08-27T07:15:00.481293Z",
             "qr_payload":"schirmziit://enroll?url=https://api.schirmziit.ch&code=A2B3C4"}
            """#,
            as: EnrollmentResponse.self
        )
        XCTAssertEqual(enrollment.code, "A2B3C4")
        XCTAssertEqual(
            PairDeviceView.serverAddress(from: enrollment.qrPayload),
            "https://api.schirmziit.ch"
        )
        XCTAssertEqual(enrollment.expiresAt.timeIntervalSince1970, 1_787_814_900, accuracy: 1)
        // An older server, or one whose public URL is too long to draw, sends
        // no square. That is a missing convenience, not a failed mint: the code
        // above still pairs the phone.
        XCTAssertNil(enrollment.qr)
    }

    func testDecodesTheSquareTheServerDrew() throws {
        let enrollment = try decode(
            #"""
            {"code":"A2B3C4","expires_at":"2026-08-27T07:15:00Z","qr_payload":"x",
             "qr":{"size":3,"rows":["101","010","101"]}}
            """#,
            as: EnrollmentResponse.self
        )
        let qr = try XCTUnwrap(enrollment.qr)
        XCTAssertTrue(qr.isDrawable)
        // Read by row, then column. A renderer fed a transposed matrix still
        // draws a plausible square, and a plausible square scans as nothing.
        XCTAssertTrue(qr.isDark(x: 0, y: 0))
        XCTAssertFalse(qr.isDark(x: 1, y: 0))
        XCTAssertTrue(qr.isDark(x: 1, y: 1))
    }

    /// Every one of these decodes, and every one of them would draw a square a
    /// camera cannot read — which a parent reads as their own phone being at
    /// fault. `isDrawable` is what keeps them off the screen.
    func testASquareThatIsNotSquareIsNotDrawn() throws {
        let ragged = try decode(
            #"{"size":3,"rows":["101","01","101"]}"#, as: QrMatrix.self
        )
        let short = try decode(#"{"size":3,"rows":["101","010"]}"#, as: QrMatrix.self)
        let empty = try decode(#"{"size":0,"rows":[]}"#, as: QrMatrix.self)
        let notModules = try decode(#"{"size":2,"rows":["1x","01"]}"#, as: QrMatrix.self)

        XCTAssertFalse(ragged.isDrawable)
        XCTAssertFalse(short.isDrawable)
        XCTAssertFalse(empty.isDrawable)
        XCTAssertFalse(notModules.isDrawable)
    }

    func testDecodesMe() throws {
        let me = try decode(
            #"{"id":"p1","email":"joris@example.ch","family_id":"f1"}"#,
            as: MeResponse.self
        )
        XCTAssertEqual(me.familyId, "f1")
    }
}
