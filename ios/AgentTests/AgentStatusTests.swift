import XCTest
@testable import SchirmziitKit

final class AgentStatusTests: XCTestCase {
    private let credentials = AgentCredentials(
        baseURL: URL(string: "https://schirmziit.example.ch")!, deviceId: "dev", token: "tok"
    )

    func testAnUnpairedPhoneAsksToPairFirst() {
        XCTAssertEqual(
            AgentStatus.derive(credentials: nil, authorization: .notDetermined, pendingHours: 0, lastSyncAt: nil),
            .needsPairing
        )
    }

    func testPairingComesBeforeThePermissionPrompt() {
        XCTAssertEqual(
            AgentStatus.derive(credentials: nil, authorization: .approved, pendingHours: 3, lastSyncAt: nil),
            .needsPairing,
            "asking for Screen Time access before there is a server to send to is noise"
        )
    }

    func testAPairedPhoneWithoutPermissionAsksForIt() {
        XCTAssertEqual(
            AgentStatus.derive(credentials: credentials, authorization: .notDetermined,
                               pendingHours: 0, lastSyncAt: nil),
            .needsScreenTimePermission
        )
    }

    func testADeclinedPromptIsNotTheSameAsAMissingEntitlement() {
        XCTAssertEqual(
            AgentStatus.derive(credentials: credentials, authorization: .denied, pendingHours: 0, lastSyncAt: nil),
            .screenTimeDenied
        )
        XCTAssertEqual(
            AgentStatus.derive(credentials: credentials, authorization: .unavailable("no entitlement"),
                               pendingHours: 0, lastSyncAt: nil),
            .screenTimeUnavailable("no entitlement")
        )
    }

    func testAnApprovedPhoneReportsWithItsQueueDepth() {
        let stamp = Date(timeIntervalSince1970: 1_787_997_600)
        XCTAssertEqual(
            AgentStatus.derive(credentials: credentials, authorization: .approved,
                               pendingHours: 4, lastSyncAt: stamp),
            .reporting(pendingHours: 4, lastSyncAt: stamp)
        )
    }
}
