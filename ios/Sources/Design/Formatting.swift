import Foundation

enum Formatting {
    /// "2 h 14 min" / "18 min" / "45 s" — never "0.23 h". Matches the dashboard.
    static func duration(_ milliseconds: Int) -> String {
        let minuteUnit = String(localized: "unit.minute")
        // Below a minute the minute is the wrong unit: a twenty-second glance at
        // a phone is not "0 min", and it is not "1 min" either.
        if milliseconds > 0, milliseconds < 60_000 {
            let seconds = Int((Double(milliseconds) / 1_000).rounded())
            // 59.5s rounds to 60, which must read as the minute it is.
            if seconds < 60 {
                let secondUnit = String(localized: "unit.second")
                return "\(seconds) \(secondUnit)"
            }
            return "1 \(minuteUnit)"
        }
        let minutes = Int((Double(milliseconds) / 60_000).rounded())
        let hourUnit = String(localized: "unit.hour")
        if minutes < 60 { return "\(minutes) \(minuteUnit)" }
        let hours = minutes / 60
        let rest = minutes % 60
        return rest == 0 ? "\(hours) \(hourUnit)" : "\(hours) \(hourUnit) \(rest) \(minuteUnit)"
    }

    /// Apps worth a row of their own, and the glances that are not.
    ///
    /// A launcher, a clock and a keyboard fill the list with rows nobody wants
    /// to talk about and push the day's real apps off the screen. They stay
    /// reachable — a parent and a child must be able to see the same numbers —
    /// but folded.
    static func splitApps(
        _ apps: [(label: String, ms: Int)]
    ) -> (shown: [(label: String, ms: Int)], brief: [(label: String, ms: Int)]) {
        var shown: [(label: String, ms: Int)] = []
        var brief: [(label: String, ms: Int)] = []
        for app in apps {
            // Rounded, not raw: the row would render "0 s", which says nothing at all.
            if Int((Double(app.ms) / 1_000).rounded()) == 0 { continue }
            if app.ms < 60_000 { brief.append(app) } else { shown.append(app) }
        }
        return (shown, brief)
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

    /// Foreground milliseconds per local day, one entry per day in `from...to`.
    ///
    /// Zero-filled: a day with no rows is a quiet day, not a missing one. Same
    /// measure as the hero total — screen-on time would be a second, different
    /// number for the same day on the same screen.
    static func dailyTotals(
        _ series: [UsageSeries],
        from: String,
        to: String
    ) -> [(day: String, ms: Int)] {
        var totals: [String: Int] = [:]
        var order: [String] = []
        var day = from
        while day <= to {
            totals[day] = 0
            order.append(day)
            guard let next = nextDay(day) else { break }
            day = next
        }
        for entry in series {
            for point in entry.points where totals[point.start] != nil {
                // Only days the response claimed: a stray point must not land
                // on day one and invent a busy Monday.
                totals[point.start, default: 0] += point.foregroundMs
            }
        }
        return order.map { (day: $0, ms: totals[$0] ?? 0) }
    }

    static func nextDay(_ day: String) -> String? {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.timeZone = calendar.timeZone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        guard let date = formatter.date(from: day),
              let next = calendar.date(byAdding: .day, value: 1, to: date) else { return nil }
        return formatter.string(from: next)
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
