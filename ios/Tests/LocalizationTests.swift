import XCTest
@testable import Nestling

/// A missing translation shows an English sentence inside a German screen. These
/// read the shipped .strings files, so the check needs no device.
final class LocalizationTests: XCTestCase {
    private let languages = ["de", "fr", "it"]

    private func strings(_ language: String) throws -> [String: String] {
        let bundle = Bundle(for: type(of: self))
        guard let url = bundle.url(forResource: "Localizable", withExtension: "strings",
                                   subdirectory: nil, localization: language)
                ?? Bundle.main.url(forResource: "Localizable", withExtension: "strings",
                                   subdirectory: nil, localization: language) else {
            throw XCTSkip("\(language).lproj not in the test bundle")
        }
        let dictionary = NSDictionary(contentsOf: url) as? [String: String]
        return try XCTUnwrap(dictionary)
    }

    func testEveryLanguageHasEveryKey() throws {
        let reference = try strings("en")
        XCTAssertFalse(reference.isEmpty)
        for language in languages {
            let translated = try strings(language)
            let missing = Set(reference.keys).subtracting(translated.keys)
            XCTAssertTrue(missing.isEmpty, "\(language) is missing: \(missing.sorted())")
        }
    }

    func testNoTranslationIsEmpty() throws {
        for language in ["en"] + languages {
            for (key, value) in try strings(language) {
                XCTAssertFalse(value.trimmingCharacters(in: .whitespaces).isEmpty, "\(language)/\(key)")
            }
        }
    }

    func testPlaceholdersSurviveTranslation() throws {
        let reference = try strings("en")
        for language in languages {
            for (key, value) in try strings(language) {
                let expected = reference[key]?.contains("%lld") ?? false
                XCTAssertEqual(value.contains("%lld"), expected, "\(language)/\(key) placeholder mismatch")
            }
        }
    }

    func testGermanUsesSwissSpellingAndTheDuForm() throws {
        let german = try strings("de")
        let text = german.values.joined(separator: " ")
        XCTAssertFalse(text.contains("ß"), "Schweizer Hochdeutsch has no ß")
        XCTAssertTrue(text.lowercased().contains("dein") || text.lowercased().contains(" du "))
    }
}
