import Foundation

/// Records what the last monitored hour was, so the app knows which hour to ask
/// the report extension for. Written by the extension, read by the app.
struct HourMarker: Sendable {
    let url: URL

    init(directory: URL = GroupContainer.directory()) {
        url = directory.appendingPathComponent("last-hour.json")
    }

    func write(hourStartMillis: Int64) {
        try? JSONEncoder().encode(["hourStartMillis": hourStartMillis]).write(to: url, options: .atomic)
    }

    func read() -> Int64? {
        guard let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode([String: Int64].self, from: data) else { return nil }
        return decoded["hourStartMillis"]
    }
}
