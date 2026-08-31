import Foundation

/// One app as the report extension is handed it, before it has an identity.
///
/// iOS gives either half of an app's identity, both, or neither: an app it will
/// not name still carries a bundle id, and an app it will not identify at all
/// can still carry a display name. `DeviceActivityResults` cannot be built in a
/// test, so the rules that turn these into rows live here rather than inside
/// `makeConfiguration`, where nothing could reach them.
struct ReportedApp: Equatable, Sendable {
    var bundleId: String?
    var name: String?
    var durationMs: Int64
    var launchCount: Int32
}

extension SnapshotApp {
    /// What an app iOS named but would not identify is keyed by. Not a bundle
    /// id and deliberately not shaped like one: the row carries the display
    /// name as its label, so this string is never what a parent reads — it is
    /// only what makes the same app land in the same row next hour.
    static let unnamedPrefix = "ios.unnamed."

    /// Fold what one hour reported into one row per app.
    ///
    /// The id used to be `bundleIdentifier ?? "unknown"`, which gave every app
    /// iOS declined to identify the same key: their minutes were summed into a
    /// single row that carried whichever display name arrived first, so a
    /// parent read one app's name over several apps' time. An app is keyed by
    /// its bundle id, or failing that by its own name, and an app with neither
    /// gets no row at all — there is nothing to put in it, and one shared word
    /// is how the merge happened in the first place. No time is lost by that:
    /// `screenOnMs` comes from the segment total, which counts an app whether
    /// or not it is nameable.
    static func fold(_ reported: [ReportedApp]) -> [SnapshotApp] {
        var totals: [String: SnapshotApp] = [:]
        var seen: [String: Int] = [:]

        for app in reported {
            let name = app.name.flatMap(nonEmpty)
            guard let key = app.bundleId.flatMap(nonEmpty) ?? name.flatMap(unnamedKey) else { continue }

            var row = totals[key] ?? SnapshotApp(bundleId: key, name: nil, durationMs: 0, launchCount: 0)
            if seen[key] == nil { seen[key] = seen.count }
            // iOS can name an app on the second segment and not the first, so
            // the first `nil` must not settle the question for the hour.
            row.name = row.name ?? name
            row.durationMs += app.durationMs
            row.launchCount += app.launchCount
            totals[key] = row
        }

        // Longest first, and ties broken by the order iOS reported them in.
        // Swift's sort is not stable, so first-seen is an explicit key rather
        // than something the input order is trusted to carry through.
        return totals.values.sorted {
            ($0.durationMs, seen[$1.bundleId] ?? 0) > ($1.durationMs, seen[$0.bundleId] ?? 0)
        }
    }

    /// `nil` when a name reduces to nothing — a name of only punctuation would
    /// otherwise key every such app to the bare prefix, which is the merge this
    /// function exists to stop.
    private static func unnamedKey(_ name: String) -> String? {
        let slug = slug(name)
        return slug.isEmpty ? nil : unnamedPrefix + slug
    }

    private static func nonEmpty(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    /// A display name reduced to something safe to carry as a key. Letters and
    /// digits survive in any script, everything else becomes one separator, so
    /// "My App!" and "My  App" cannot become two rows for one app.
    private static func slug(_ name: String) -> String {
        var out = ""
        var pendingSeparator = false
        for character in name.lowercased() {
            if character.isLetter || character.isNumber {
                if pendingSeparator, !out.isEmpty { out.append("-") }
                pendingSeparator = false
                out.append(character)
            } else {
                pendingSeparator = true
            }
        }
        return out
    }
}
