import Foundation
import XCTest
@testable import SchirmziitKit

/// The generated `ErrorCopy.strings` is a dictionary the existing localisation
/// tests never read, and a dictionary the copy tests do not read is a hole.
///
/// These read `copy/errors.toml` from the source tree, because that file is the
/// single source both the strings and the weights come from — asserting against
/// a second hand-written list here would just be the drift this is meant to catch.
final class ErrorCopyTests: XCTestCase {
    /// `weight` and `reach` per code, parsed straight from the catalog.
    private struct Entry {
        var weight = ""
        var reach: [String] = []
    }

    private static let catalog: [String: Entry] = {
        let source = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()  // ios/Tests
            .deletingLastPathComponent()  // ios
            .deletingLastPathComponent()  // repo root
            .appendingPathComponent("copy/errors.toml")
        guard let text = try? String(contentsOf: source, encoding: .utf8) else { return [:] }

        var entries: [String: Entry] = [:]
        var current: String?
        for line in text.split(separator: "\n", omittingEmptySubsequences: false) {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("[SZ-E"), trimmed.hasSuffix("]") {
                current = String(trimmed.dropFirst().dropLast())
                entries[current!] = Entry()
            } else if let key = current, trimmed.hasPrefix("weight") {
                entries[key]?.weight = trimmed.components(separatedBy: "\"").dropFirst().first ?? ""
            } else if let key = current, trimmed.hasPrefix("reach") {
                entries[key]?.reach = trimmed
                    .components(separatedBy: "\"")
                    .enumerated()
                    .filter { $0.offset % 2 == 1 }
                    .map(\.element)
            }
        }
        return entries
    }()

    private func strings(_ language: String) throws -> [String: String] {
        let bundle = Bundle(for: type(of: self))
        guard let url = bundle.url(
            forResource: "ErrorCopy", withExtension: "strings",
            subdirectory: nil, localization: language
        ) ?? Bundle.schirmziitKit.url(
            forResource: "ErrorCopy", withExtension: "strings",
            subdirectory: nil, localization: language
        ) else {
            throw XCTSkip("\(language).lproj/ErrorCopy.strings not in the test bundle")
        }
        return try XCTUnwrap(NSDictionary(contentsOf: url) as? [String: String])
    }

    func testTheCatalogWasActuallyRead() {
        XCTAssertFalse(Self.catalog.isEmpty, "copy/errors.toml was not found next to the tests")
    }

    /// A code the app can emit with no copy renders as the SZ-E901 wording,
    /// which is a lie about what happened.
    func testEveryCodeIosCanEmitHasCopyInEveryLanguage() throws {
        for (wire, entry) in Self.catalog where entry.reach.contains("ios") {
            for language in ["en", "de", "fr", "it"] {
                let table = try strings(language)
                XCTAssertNotNil(table["error.\(wire).title"], "\(language) is missing \(wire).title")
                XCTAssertNotNil(table["error.\(wire).action"], "\(language) is missing \(wire).action")
            }
        }
    }

    func testGermanErrorCopyKeepsSwissSpelling() throws {
        let german = try strings("de").values.joined(separator: " ")
        XCTAssertFalse(german.contains("ß"), "Schweizer Hochdeutsch has no ß")
    }

    /// The same rule the app and the site are already held to.
    func testNoErrorCopyImpliesSecrecy() throws {
        let forbidden = ["heimlich", "sneak", "en cachette", "di nascosto"]
        for language in ["en", "de", "fr", "it"] {
            let text = try strings(language).values.joined(separator: " ").lowercased()
            for word in forbidden {
                XCTAssertFalse(text.contains(word), "\(language) error copy contains \(word)")
            }
        }
    }

    /// `ErrorCopy.isUrgent` is a second statement of a fact `copy/errors.toml`
    /// already holds. Two sources of truth for one fact is how they drift, so
    /// this pins them together.
    func testUrgencyAgreesWithTheCatalogWeight() {
        for code in ErrorCode.everyCase {
            guard let entry = Self.catalog[code.wire] else {
                XCTFail("\(code.wire) is not in copy/errors.toml")
                continue
            }
            XCTAssertEqual(
                ErrorCopy.isUrgent(code),
                entry.weight == "urgent",
                "\(code.wire) is \(entry.weight) in the catalog but the app disagrees"
            )
        }
    }

    func testACodeWithNoIosCopyStillReadsAsSomething() {
        // SZ-E603 is Android-only. A parent must never be shown a raw key.
        let title = ErrorCopy.title(for: .mediaNotificationAccessMissing)
        XCTAssertFalse(title.contains("error.SZ-E603"), title)
        XCTAssertFalse(title.isEmpty)
    }
}
