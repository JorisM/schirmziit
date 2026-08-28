import XCTest
@testable import SchirmziitKit

/// `ChildDetailView` is a plain SwiftUI `View` struct with no `@Observable`
/// model to hand a fake transport to, and its `@State` only reliably behaves
/// once SwiftUI has actually installed the view (calling `load`/`loadStrip`
/// directly on a bare, never-rendered instance and reading `@State` back was
/// tried here and found to silently lose the write — not a mistake worth
/// repeating). So these tests target `fetchUsage`, the plain `static func`
/// both loading methods funnel through: no `@State`, no view lifecycle, just
/// the do/catch that turns a captcha page or a 500 into a `.failure` instead
/// of a value nobody checks.
final class ChildDetailViewTests: XCTestCase {
    private func stubbedClient(_ handler: @escaping @Sendable (URLRequest) -> (Int, Data)) async -> ApiClient {
        StubURLProtocol.handler = handler
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubURLProtocol.self]
        let client = ApiClient(session: URLSession(configuration: config))
        await client.configure(baseURL: URL(string: "https://parent.example.test"))
        return client
    }

    private let okBody = Data(
        #"""
        {"child_id":"kid","from":"2026-08-24","to":"2026-08-24","bucket":"hour","tz":"Europe/Zurich",
         "devices":[],"series":[],"device_totals":[]}
        """#.utf8
    )

    private let insightBody = Data(
        #"""
        {"child_id":"kid","tz":"Europe/Zurich","week":{
          "from":"2026-08-13","to":"2026-08-19","previous_from":"2026-08-06","previous_to":"2026-08-12",
          "total_ms":3600000,"previous_total_ms":1800000,"evening_ms":600000,"previous_evening_ms":300000,
          "evening_from_hour":21,"movers":[],"previous_measured":true}}
        """#.utf8
    )

    private let problemBody = Data(
        #"{"type":"about:blank","title":"error","status":502,"detail":"bad gateway","code":"SZ-E901","ref":"aa11bb"}"#.utf8
    )

    /// The finding this test exists for: a 502 must come back as a `.failure`
    /// carrying the server's own code and reference, never as a value a caller
    /// can read as "nothing to report" and zero-fill a fortnight from.
    ///
    /// It carries the code, not the server's `detail`: that sentence is English
    /// and exists for the log and the copy-details block.
    func testFetchUsageReturnsTheProblemCodeOnFailure() async {
        let client = await stubbedClient { _ in (502, self.problemBody) }

        let outcome = await ChildDetailView.fetchUsage(
            client: client, childId: "kid", from: "2026-08-11", to: "2026-08-24", bucket: "day"
        )

        guard case .failure(let error) = outcome else {
            return XCTFail("a 502 must be a failure, got \(outcome)")
        }
        XCTAssertEqual(error.code.wire, "SZ-E901")
        XCTAssertEqual(error.ref, "aa11bb")
    }

    func testFetchUsageReturnsTheDecodedResponseOnSuccess() async {
        let client = await stubbedClient { _ in (200, self.okBody) }

        let outcome = await ChildDetailView.fetchUsage(
            client: client, childId: "kid", from: "2026-08-24", to: "2026-08-24", bucket: "hour"
        )

        guard case .success(let usage) = outcome else {
            return XCTFail("a 200 must be a success, got \(outcome)")
        }
        XCTAssertEqual(usage.childId, "kid")
    }

    /// The week is an extra on this screen, and it fails like everything else:
    /// as a `.failure` the view can put a banner over, never as a value the
    /// card could paint as a quiet week.
    func testFetchInsightReturnsTheProblemCodeOnFailure() async {
        let client = await stubbedClient { _ in (502, self.problemBody) }

        let outcome = await ChildDetailView.fetchInsight(client: client, childId: "kid", date: "2026-08-20")

        guard case .failure(let error) = outcome else {
            return XCTFail("a 502 must be a failure, got \(outcome)")
        }
        XCTAssertEqual(error.code.wire, "SZ-E901")
    }

    func testFetchInsightAsksForTheDateAndZoneItWasGiven() async {
        var asked = ""
        let client = await stubbedClient { request in
            asked = request.url?.absoluteString ?? ""
            return (200, self.insightBody)
        }

        let outcome = await ChildDetailView.fetchInsight(client: client, childId: "kid", date: "2026-08-20")

        guard case .success(let insight) = outcome else {
            return XCTFail("a 200 must be a success, got \(outcome)")
        }
        XCTAssertEqual(insight.week.totalMs, 3_600_000)
        XCTAssertTrue(asked.contains("date=2026-08-20"), asked)
        XCTAssertTrue(asked.contains("tz=\(TimeZone.current.identifier)"), asked)
    }

    /// A transport-level failure (no server, DNS, a hung connection) must not be
    /// read any differently than a problem response — both are "could not load".
    func testFetchUsageReturnsAFailureWhenTheServerIsUnreachable() async {
        let client = ApiClient(session: URLSession(configuration: .ephemeral))
        await client.configure(baseURL: nil)

        let outcome = await ChildDetailView.fetchUsage(
            client: client, childId: "kid", from: "2026-08-11", to: "2026-08-24", bucket: "day"
        )

        guard case .failure = outcome else {
            return XCTFail("an unconfigured client must be a failure, got \(outcome)")
        }
    }

    /// `dayOnly` is shared by `ChildDetailView` and `AgentModel`; every caller
    /// pairs its result with a `tz=` request parameter built from
    /// `TimeZone.current`. Left on `ISO8601DateFormatter`'s GMT default, the
    /// date string and the `tz` parameter answer for two different days for the
    /// first hour or two after local midnight in a zone ahead of UTC — exactly
    /// the window a teenager in Zurich is most likely checking.
    func testDayOnlyFormatsInTheLocalZoneNotGMT() {
        XCTAssertEqual(
            ISO8601DateFormatter.dayOnly.timeZone,
            TimeZone.current,
            "dayOnly must format in this device's zone, not GMT"
        )

        // The bug, made concrete: 2026-08-25T00:30 in Zurich (CEST, UTC+2) is
        // still 2026-08-24T22:30 in UTC.
        let zurich = TimeZone(identifier: "Europe/Zurich")!
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zurich
        let instant = calendar.date(
            from: DateComponents(year: 2026, month: 8, day: 25, hour: 0, minute: 30)
        )!

        var local = ISO8601DateFormatter()
        local.formatOptions = [.withFullDate]
        local.timeZone = zurich
        XCTAssertEqual(local.string(from: instant), "2026-08-25")

        var gmt = ISO8601DateFormatter()
        gmt.formatOptions = [.withFullDate]
        XCTAssertEqual(gmt.string(from: instant), "2026-08-24")
    }
}
