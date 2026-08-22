import XCTest
@testable import SchirmziitAgentKit

/// The iOS agent must send exactly what the server documents. The Rust core
/// builds the body, so this test is really about the third copy of the contract:
/// it fails if `api/openapi.json` and the core ever drift apart.
final class ContractTests: XCTestCase {
    private func schema() throws -> [String: Any] {
        let bundle = Bundle(for: type(of: self))
        guard let url = bundle.url(forResource: "openapi", withExtension: "json") else {
            throw XCTSkip("openapi.json is not in the test bundle")
        }
        let json = try JSONSerialization.jsonObject(with: try Data(contentsOf: url))
        return try XCTUnwrap(json as? [String: Any])
    }

    private func properties(of name: String) throws -> Set<String> {
        let components = try XCTUnwrap(try schema()["components"] as? [String: Any])
        let schemas = try XCTUnwrap(components["schemas"] as? [String: Any])
        let target = try XCTUnwrap(schemas[name] as? [String: Any], "\(name) is not in the OpenAPI document")
        let properties = try XCTUnwrap(target["properties"] as? [String: Any])
        // Guards against the whole check passing vacuously if the document ever
        // ships an empty schema.
        XCTAssertFalse(properties.isEmpty, "\(name) has no properties")
        return Set(properties.keys)
    }

    private func body() throws -> [String: Any] {
        let json = try ingestBody(hours: [pendingHour().ffi], deviceTimeMillis: hour + anHour)
        return try XCTUnwrap(try JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any])
    }

    func testTheRequestCarriesEveryDocumentedField() throws {
        XCTAssertEqual(Set(try body().keys), try properties(of: "IngestRequest"))
    }

    func testAnHourCarriesEveryDocumentedField() throws {
        let hours = try XCTUnwrap(try body()["hours"] as? [[String: Any]])
        XCTAssertEqual(Set(try XCTUnwrap(hours.first).keys), try properties(of: "IngestHour"))
    }

    func testAnAppCarriesEveryDocumentedField() throws {
        let hours = try XCTUnwrap(try body()["hours"] as? [[String: Any]])
        let apps = try XCTUnwrap(try XCTUnwrap(hours.first)["apps"] as? [[String: Any]])
        XCTAssertEqual(Set(try XCTUnwrap(apps.first).keys), try properties(of: "IngestApp"))
    }

    /// Timestamps go over the wire as RFC3339, not epoch millis — the millis only
    /// exist because `chrono::DateTime` cannot cross the FFI boundary.
    func testTimestampsAreSentAsRfc3339() throws {
        let hours = try XCTUnwrap(try body()["hours"] as? [[String: Any]])
        let hourStart = try XCTUnwrap(try XCTUnwrap(hours.first)["hour_start"] as? String)
        XCTAssertTrue(hourStart.hasSuffix("Z"), hourStart)
        XCTAssertNotNil(ISO8601DateFormatter().date(from: hourStart))
    }

    func testTheSchemaVersionMatchesTheCore() throws {
        XCTAssertEqual(try body()["schema"] as? Int, 1)
    }
}
