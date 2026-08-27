import Foundation
import XCTest
@testable import SchirmziitKit

final class AppErrorTests: XCTestCase {
    override func setUp() {
        ErrorLog.shared.clear()
    }

    func testAProblemKeepsTheServersCodeAndReference() {
        let error = AppError(
            problem: ApiProblem(
                type: "t", title: "t", status: 404, detail: "not found",
                code: "SZ-E201", ref: "7f3a9c"
            ),
            endpoint: "/v1/children"
        )
        XCTAssertEqual(error.code.wire, "SZ-E201")
        XCTAssertEqual(error.ref, "7f3a9c")
        XCTAssertEqual(error.httpStatus, 404)
    }

    /// A failure that never reached the server has no server reference, so it
    /// makes its own — otherwise the mono line is blank exactly when the parent
    /// has nothing else to report.
    func testALocalFailureMakesItsOwnReference() {
        let error = AppError.transport(URLError(.notConnectedToInternet), endpoint: "/v1/children")
        XCTAssertEqual(error.ref.count, 6)
        XCTAssertEqual(error.code.wire, "SZ-E501")
    }

    /// The cases a parent can act on differently: a tunnel, a slow server, a
    /// certificate, a wrong address. A browser reports all of these as one
    /// opaque failure; URLSession tells them apart, which is why iOS carries
    /// codes the dashboard cannot honestly emit.
    func testTheUrlErrorsWorthTellingApartAreToldApart() {
        XCTAssertEqual(AppError.transport(URLError(.timedOut), endpoint: nil).code.wire, "SZ-E502")
        XCTAssertEqual(
            AppError.transport(URLError(.secureConnectionFailed), endpoint: nil).code.wire,
            "SZ-E503"
        )
        XCTAssertEqual(
            AppError.transport(URLError(.cannotFindHost), endpoint: nil).code.wire,
            "SZ-E505"
        )
    }

    func testTheCopyBlockLeadsWithTheCodeAndReference() {
        let error = AppError(
            problem: ApiProblem(
                type: "t", title: "t", status: 502, detail: "bad gateway",
                code: "SZ-E504", ref: "7f3a9c"
            ),
            endpoint: "/v1/children"
        )
        XCTAssertEqual(error.copyDetails.split(separator: "\n").first, "SZ-E504 · 7f3a9c")
        XCTAssertTrue(error.copyDetails.contains("GET /v1/children → 502"), error.copyDetails)
    }

    /// The endpoint is a path. A self-hoster pasting a screenshot into a public
    /// issue must not publish the address of the machine in their flat.
    func testTheCopyBlockNeverCarriesTheHost() {
        let error = AppError(
            problem: ApiProblem(
                type: "t", title: "t", status: 404,
                detail: "anna@example.ch asked for Mia",
                code: "SZ-E201", ref: "abc123"
            ),
            endpoint: "https://home.example.ch/v1/children/mia"
        )
        XCTAssertFalse(error.copyDetails.contains("home.example.ch"), error.copyDetails)
        XCTAssertFalse(error.copyDetails.contains("anna@example.ch"), error.copyDetails)
        XCTAssertFalse(error.copyDetails.contains("Mia"), error.copyDetails)
    }

    /// An unknown code must not become a silent success or a crash: the app and
    /// the server it talks to can be different versions.
    func testAnUnknownWireCodeFallsBackToTheServerError() {
        let error = AppError(
            problem: ApiProblem(
                type: "t", title: "t", status: 500, detail: "x",
                code: "SZ-E999", ref: "abc123"
            ),
            endpoint: "/v1/me"
        )
        XCTAssertEqual(error.code.wire, "SZ-E901")
    }

    /// Recording is synchronous on purpose: an entry that lands after the error
    /// it describes is already on screen is an entry missing from the report.
    func testTheLogKeepsTheLastFiftyNewestLast() {
        for index in 0..<60 {
            _ = AppError.transport(URLError(.timedOut), endpoint: "/v1/\(index)")
        }
        let log = ErrorLog.shared.recent()
        XCTAssertEqual(log.count, 50)
        XCTAssertEqual(log.first?.endpoint, "/v1/10")
        XCTAssertEqual(log.last?.endpoint, "/v1/59")
    }
}
