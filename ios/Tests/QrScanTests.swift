import AVFoundation
import XCTest
@testable import SchirmziitKit

/// The reader between the camera and the pairing form.
///
/// A camera is not a button: it hands the same payload back for every frame the
/// square stays in view, thirty times a second. Everything that matters about
/// that — answering once, and not shouting at a boarding pass — is decided here
/// rather than in the view, because the view cannot be run in a test.
final class ScanReaderTests: XCTestCase {

    func testAPairingSquareIsTheOneThingThisReads() {
        var reader = ScanReader()

        guard case .enroll(let link) = reader.read("schirmziit://enroll?url=https://api.schirmziit.ch&code=k7mnpq") else {
            return XCTFail("a pairing square should fill the form")
        }
        XCTAssertEqual(link.server, "https://api.schirmziit.ch")
        XCTAssertEqual(link.code, "K7MNPQ")
    }

    func testACodeIsAnsweredOnceHoweverLongItStaysInFrame() {
        var reader = ScanReader()
        let square = "schirmziit://enroll?url=https://api.schirmziit.ch&code=A2B3C4"

        guard case .enroll = reader.read(square) else { return XCTFail("first frame should be the answer") }
        // Every frame after it is the same square, still being held up. Filling
        // the form again would be harmless; reopening a screen that has already
        // closed, or spending a one-shot code twice, would not be.
        XCTAssertEqual(reader.read(square), .again)
        XCTAssertEqual(reader.read(square), .again)
    }

    func testSomethingElseIsRefusedOnceNotThirtyTimesASecond() {
        var reader = ScanReader()

        XCTAssertEqual(reader.read("https://www.sbb.ch/ticket/1234"), .notOurs)
        XCTAssertEqual(reader.read("https://www.sbb.ch/ticket/1234"), .again)
    }

    func testADifferentWrongSquareIsWorthSayingSoAgain() {
        var reader = ScanReader()

        XCTAssertEqual(reader.read("https://www.sbb.ch/ticket/1234"), .notOurs)
        // A new square in front of the lens is a new attempt by whoever is
        // holding the phone, and deserves the answer again.
        XCTAssertEqual(reader.read("WIFI:S=Familie;T=WPA;P=hunter2;;"), .notOurs)
    }

    func testAnUnencryptedAddressIsNotOursEvenSpeltLikeOurLink() {
        var reader = ScanReader()

        // The strictness lives in `EnrollLink`, and this is the path that has to
        // keep going through it: a square is the one way a stranger's address
        // reaches this app without anyone typing it.
        XCTAssertEqual(reader.read("schirmziit://enroll?url=http://evil.example&code=A2B3C4"), .notOurs)
    }
}

/// What the system says about the camera, and what this screen does about it.
final class ScanAccessTests: XCTestCase {

    func testEachAnswerFromTheSystemHasOneMeaningHere() {
        XCTAssertEqual(ScanAccess.of(.authorized, hasCamera: true), .ready)
        XCTAssertEqual(ScanAccess.of(.notDetermined, hasCamera: true), .ask)
        XCTAssertEqual(ScanAccess.of(.denied, hasCamera: true), .refused)
        // Screen Time or a managed profile can turn the camera off for the whole
        // phone. Nothing this app asks will change that, so it reads as refused
        // and the typed code stays the way through.
        XCTAssertEqual(ScanAccess.of(.restricted, hasCamera: true), .refused)
    }

    func testAPhoneWithNoCameraIsNotAPhoneThatRefusedOne() {
        // A simulator, or an iPad without a back camera. Telling that child to
        // grant camera access in Settings sends them looking for a switch that
        // is not there.
        XCTAssertEqual(ScanAccess.of(.authorized, hasCamera: false), .noCamera)
        XCTAssertEqual(ScanAccess.of(.notDetermined, hasCamera: false), .noCamera)
    }
}
