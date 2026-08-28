import XCTest
@testable import SchirmziitKit

/// What a scanned square is allowed to do to this phone.
///
/// Two separate risks. The link decides where a child's usage data goes, so a
/// plaintext or foreign URL must not be followed; and the link arrives from
/// outside the app, so it must not be able to change what this phone *is*.
final class EnrollLinkTests: XCTestCase {
    func testAMintedLinkReadsBackAsAServerAndACode() {
        let link = EnrollLink("schirmziit://enroll?url=https://api.schirmziit.ch&code=k7mnpq")

        XCTAssertEqual(link?.server, "https://api.schirmziit.ch")
        // Uppercased here, because the server's alphabet is: a code read back
        // in lower case is refused at the door for no reason a child could see.
        XCTAssertEqual(link?.code, "K7MNPQ")
    }

    func testATrailingSlashIsNotPartOfTheAddress() {
        // `https://host/` and `https://host` are the same server, and the app
        // builds paths onto whichever it is given — the double slash that
        // results is a 404 on some proxies.
        XCTAssertEqual(
            EnrollLink("schirmziit://enroll?url=https://api.schirmziit.ch/&code=A2B3C4")?.server,
            "https://api.schirmziit.ch"
        )
    }

    func testPlaintextIsRefusedAndLocalhostIsNot() {
        // A child's phone pointed at http:// uploads a fortnight of a child's
        // day in the clear. Localhost is the one exception, and it is the one
        // address no network carries.
        XCTAssertNil(EnrollLink("schirmziit://enroll?url=http://api.schirmziit.ch&code=A2B3C4"))
        XCTAssertNotNil(EnrollLink("schirmziit://enroll?url=http://localhost:8099&code=A2B3C4"))
        XCTAssertNotNil(EnrollLink("schirmziit://enroll?url=http://127.0.0.1:8099&code=A2B3C4"))
    }

    func testAnythingThatIsNotAnEnrolLinkIsNotOne() {
        XCTAssertNil(EnrollLink("https://api.schirmziit.ch/enroll?code=A2B3C4"))
        XCTAssertNil(EnrollLink("schirmziit://sync"))
        XCTAssertNil(EnrollLink("schirmziit://enroll?url=https://api.schirmziit.ch"))
        XCTAssertNil(EnrollLink("schirmziit://enroll?url=https://api.schirmziit.ch&code="))
        XCTAssertNil(EnrollLink("nonsense"))
    }

    // ─── what the link may change ────────────────────────────────────────

    func testAPhoneAskingForACodeGetsItFilledIn() {
        let link = EnrollLink("schirmziit://enroll?url=https://api.schirmziit.ch&code=A2B3C4")

        XCTAssertEqual(EnrollLinkRoute.decide(role: .child, link: link), .fillPairingForm(link!))
    }

    func testAPhoneWithNoRoleYetGoesToChildSetup() {
        let link = EnrollLink("schirmziit://enroll?url=https://api.schirmziit.ch&code=A2B3C4")

        XCTAssertEqual(EnrollLinkRoute.decide(role: nil, link: link), .startChildSetup(link!))
    }

    func testAParentsOwnPhoneIsNeverTurnedIntoAChildsByALink() {
        // A parent scanning the square they just minted is a parent checking
        // their own screen. Repurposing the phone they read the dashboard on
        // would end their session and hand the phone to child mode.
        let link = EnrollLink("schirmziit://enroll?url=https://api.schirmziit.ch&code=A2B3C4")

        XCTAssertEqual(EnrollLinkRoute.decide(role: .parent, link: link), .ignore)
    }

    func testALinkThisAppCannotReadChangesNothing() {
        XCTAssertEqual(EnrollLinkRoute.decide(role: nil, link: EnrollLink("nonsense")), .ignore)
    }
}
