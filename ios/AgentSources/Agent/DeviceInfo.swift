import UIKit

enum DeviceInfo {
    /// "iPhone15,3" — the same identifier Apple's own crash reports use. The
    /// marketing name is not available on device without a lookup table that
    /// goes stale every September.
    static var model: String {
        var info = utsname()
        uname(&info)
        let machine = withUnsafeBytes(of: &info.machine) { bytes in
            String(cString: bytes.baseAddress!.assumingMemoryBound(to: CChar.self))
        }
        return machine.isEmpty ? "iOS" : machine
    }

    /// What the child called their phone in Settings, e.g. "Emmas iPhone".
    static var name: String { UIDevice.current.name }
}

enum AgentFormatting {
    /// "2 h 14 min" / "18 min" — same shape as the dashboard.
    static func duration(_ milliseconds: Int64) -> String {
        let minutes = Int((Double(milliseconds) / 60_000).rounded())
        let hourUnit = String(localized: "unit.hour")
        let minuteUnit = String(localized: "unit.minute")
        if minutes < 60 { return "\(minutes) \(minuteUnit)" }
        return minutes % 60 == 0
            ? "\(minutes / 60) \(hourUnit)"
            : "\(minutes / 60) \(hourUnit) \(minutes % 60) \(minuteUnit)"
    }
}
