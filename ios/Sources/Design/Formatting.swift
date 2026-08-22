import Foundation

enum Formatting {
    /// "2 h 14 min" / "18 min" — never "0.23 h". Matches the dashboard.
    static func duration(_ milliseconds: Int) -> String {
        let minutes = Int((Double(milliseconds) / 60_000).rounded())
        let hourUnit = String(localized: "unit.hour")
        let minuteUnit = String(localized: "unit.minute")
        if minutes < 60 { return "\(minutes) \(minuteUnit)" }
        let hours = minutes / 60
        let rest = minutes % 60
        return rest == 0 ? "\(hours) \(hourUnit)" : "\(hours) \(hourUnit) \(rest) \(minuteUnit)"
    }

    /// Screen-on milliseconds per local hour, 0..23.
    ///
    /// The server already applied the caller's timezone, so the local hour is the
    /// field after the T. Parsed strictly: slicing blindly turns an unparseable
    /// value into hour 0, which paints phantom midnight usage — the one thing the
    /// ribbon exists to reveal.
    static func hoursFromTotals(_ totals: [DeviceTotal]) -> [Int] {
        var perHour = [Int](repeating: 0, count: 24)
        for total in totals {
            guard let hour = localHour(from: total.start) else { continue }
            perHour[hour] += total.screenOnMs
        }
        return perHour
    }

    static func localHour(from timestamp: String) -> Int? {
        // "2026-08-21T15:00:00+02:00"
        guard timestamp.count >= 13 else { return nil }
        let parts = timestamp.split(separator: "T", maxSplits: 1)
        guard parts.count == 2, parts[0].count == 10 else { return nil }
        let hourText = parts[1].prefix(2)
        guard hourText.count == 2, let hour = Int(hourText), (0..<24).contains(hour) else { return nil }
        return hour
    }

    /// Sequential ramp step for a magnitude, relative to the busiest hour.
    static func rampStep(ms: Int, busiest: Int) -> Int {
        guard ms > 0, busiest > 0 else { return 0 }
        let share = Double(ms) / Double(busiest)
        switch share {
        case 0.8...: return 5
        case 0.6..<0.8: return 4
        case 0.4..<0.6: return 3
        case 0.2..<0.4: return 2
        default: return 1
        }
    }
}

extension String {
    init(localized key: String.LocalizationValue) {
        // Explicitly this framework's bundle. `Bundle.main` is the app when the
        // app runs and the *test runner* when tests run, so a main-bundle lookup
        // silently returns the raw key under test.
        self.init(localized: key, table: nil, bundle: .schirmziitKit)
    }
}

private final class BundleToken {}

extension Bundle {
    /// The framework's own bundle, whichever host embedded it.
    static let schirmziitKit = Bundle(for: BundleToken.self)
}
