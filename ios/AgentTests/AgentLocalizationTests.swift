import XCTest
@testable import SchirmziitKit

/// A missing translation shows an English sentence inside a German screen — on a
/// child's phone, in the app that explains what is being measured.
final class AgentLocalizationTests: XCTestCase {
    private let languages = ["de", "fr", "it"]

    private func strings(_ language: String) throws -> [String: String] {
        let bundle = Bundle(for: type(of: self))
        guard let url = bundle.url(forResource: "Localizable", withExtension: "strings",
                                   subdirectory: nil, localization: language)
                ?? Bundle.main.url(forResource: "Localizable", withExtension: "strings",
                                   subdirectory: nil, localization: language) else {
            throw XCTSkip("\(language).lproj not in the test bundle")
        }
        return try XCTUnwrap(NSDictionary(contentsOf: url) as? [String: String])
    }

    func testEveryLanguageHasEveryKey() throws {
        let reference = try strings("en")
        XCTAssertGreaterThan(reference.count, 40)
        for language in languages {
            let missing = Set(reference.keys).subtracting(try strings(language).keys)
            XCTAssertTrue(missing.isEmpty, "\(language) is missing: \(missing.sorted())")
        }
    }

    func testNoLanguageHasKeysTheOthersLack() throws {
        let reference = Set(try strings("en").keys)
        for language in languages {
            let extra = Set(try strings(language).keys).subtracting(reference)
            XCTAssertTrue(extra.isEmpty, "\(language) has stray keys: \(extra.sorted())")
        }
    }

    func testNoTranslationIsEmpty() throws {
        for language in ["en"] + languages {
            for (key, value) in try strings(language) {
                XCTAssertFalse(value.trimmingCharacters(in: .whitespaces).isEmpty, "\(language)/\(key)")
            }
        }
    }

    func testGermanUsesSwissSpellingAndSpeaksToTheChildDirectly() throws {
        let german = try strings("de")
        let text = german.values.joined(separator: " ")
        XCTAssertFalse(text.contains("ß"), "Schweizer Hochdeutsch has no ß")
        XCTAssertTrue(text.lowercased().contains("dein") || text.lowercased().contains(" du "))
    }

    /// The product promise: the child is told what happens, in plain words.
    func testEveryLanguageSaysNothingIsBlockedAndNothingIsHidden() throws {
        for language in ["en"] + languages {
            let strings = try strings(language)
            XCTAssertNotNil(strings["agent.help.never.5"], "\(language) must keep the 'nothing is blocked' line")
            XCTAssertNotNil(strings["agent.help.yousee"], "\(language) must keep the 'you can see everything' line")
        }
    }
}
