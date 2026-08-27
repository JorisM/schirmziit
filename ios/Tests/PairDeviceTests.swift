import XCTest
@testable import SchirmziitKit

/// The two pure decisions the pairing card makes, kept out of the view for the
/// reason `ChildDetailViewTests` records: `@State` on a view SwiftUI has never
/// installed silently loses writes, so what is worth asserting lives in
/// `static func`s.
///
/// Both are about the same failure — a parent reading something out loud that
/// the child's phone will not accept. A wrong server address enrols the phone
/// exactly once and then never reports again, and an expired code is refused at
/// the door.
final class PairDeviceTests: XCTestCase {
    /// The payload is `schirmziit://enroll?url=…&code=…` and is meant for a
    /// camera. A parent types the address, so it has to come out of the link.
    func testTheServerAddressIsReadOutOfTheDeepLink() {
        XCTAssertEqual(
            PairDeviceView.serverAddress(
                from: "schirmziit://enroll?url=https://api.schirmziit.ch&code=A2B3C4"
            ),
            "https://api.schirmziit.ch"
        )
    }

    /// A self-hoster's server could send a payload this app cannot parse — a
    /// scheme change, an added parameter, an older or newer server. Showing the
    /// raw string beats showing nothing: the parent still has something to
    /// compare against what they typed.
    func testAnUnparseablePayloadFallsBackToItself() {
        XCTAssertEqual(PairDeviceView.serverAddress(from: "not a link"), "not a link")
        XCTAssertEqual(
            PairDeviceView.serverAddress(from: "schirmziit://enroll?code=A2B3C4"),
            "schirmziit://enroll?code=A2B3C4",
            "no url parameter means no address to show, not an empty line"
        )
    }

    /// The server's window is exclusive — `expires_at > now()` — so the instant
    /// it names is already refused. A code shown as usable one second past that
    /// sends a parent to a phone that says no.
    func testACodeIsExpiredFromTheInstantItNames() {
        let expires = Date(timeIntervalSince1970: 1_000_000)

        XCTAssertFalse(PairDeviceView.isExpired(expires, now: expires.addingTimeInterval(-1)))
        XCTAssertTrue(
            PairDeviceView.isExpired(expires, now: expires),
            "the server rejects a code at expires_at itself"
        )
        XCTAssertTrue(PairDeviceView.isExpired(expires, now: expires.addingTimeInterval(1)))
    }
}
