import XCTest
@testable import SchirmziitAgentKit

/// People type "schirmziit.example.ch", not a URL. Getting this wrong means a
/// child cannot pair and has no way to tell why.
final class ServerAddressTests: XCTestCase {
    @MainActor
    func testAcceptsWhatPeopleActuallyType() {
        XCTAssertEqual(AgentModel.normalisedServer("schirmziit.example.ch")?.absoluteString,
                       "https://schirmziit.example.ch")
        XCTAssertEqual(AgentModel.normalisedServer("  schirmziit.example.ch  ")?.absoluteString,
                       "https://schirmziit.example.ch")
        XCTAssertEqual(AgentModel.normalisedServer("https://schirmziit.example.ch/")?.absoluteString,
                       "https://schirmziit.example.ch")
        XCTAssertEqual(AgentModel.normalisedServer("http://192.168.1.10:8080")?.absoluteString,
                       "http://192.168.1.10:8080",
                       "a self-hosted server on the LAN is a normal case")
    }

    @MainActor
    func testRejectsWhatCannotBeAServer() {
        XCTAssertNil(AgentModel.normalisedServer(""))
        XCTAssertNil(AgentModel.normalisedServer("   "))
        XCTAssertNil(AgentModel.normalisedServer("nothost"))
        XCTAssertNil(AgentModel.normalisedServer("ftp://schirmziit.example.ch"))
    }
}
