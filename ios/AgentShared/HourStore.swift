import Foundation

protocol HourStore: Sendable {
    func pending() throws -> [PendingHour]
    /// Adds rows, keeping the fuller version of any hour already stored.
    func merge(_ hours: [PendingHour]) throws
    func replace(with hours: [PendingHour]) throws
}

/// A JSON file in the shared container. Small by construction: one row per hour,
/// capped at `maxRows`, so it stays a single readable file rather than a database.
struct FileHourStore: HourStore {
    /// 14 days of hours. Older rows are dropped, not sent: the parent side keeps
    /// 13 months, and a phone that has been offline for two weeks has bigger
    /// problems than a gap in the ribbon.
    static let maxRows = 14 * 24

    let url: URL
    var maxRows: Int = FileHourStore.maxRows

    init(url: URL, maxRows: Int = FileHourStore.maxRows) {
        self.url = url
        self.maxRows = maxRows
    }

    init(directory: URL = GroupContainer.directory(), maxRows: Int = FileHourStore.maxRows) {
        self.init(url: directory.appendingPathComponent("pending-hours.json"), maxRows: maxRows)
    }

    func pending() throws -> [PendingHour] {
        guard let data = FileManager.default.contents(atPath: url.path) else { return [] }
        if data.isEmpty { return [] }
        return try JSONDecoder().decode([PendingHour].self, from: data)
    }

    func merge(_ hours: [PendingHour]) throws {
        var byHour: [Int64: PendingHour] = [:]
        for row in try pending() { byHour[row.hourStartMillis] = row }

        for incoming in hours {
            guard let existing = byHour[incoming.hourStartMillis] else {
                byHour[incoming.hourStartMillis] = incoming
                continue
            }
            // The report extension can be asked for an hour that is still
            // running, or for a window that happens to exclude an app. Taking
            // the newer row unconditionally would shrink an hour that was
            // already fuller — the exact bug the Android agent shipped once.
            if incoming.weight >= existing.weight {
                byHour[incoming.hourStartMillis] = incoming
            }
        }

        try write(byHour.values.sorted { $0.hourStartMillis < $1.hourStartMillis })
    }

    func replace(with hours: [PendingHour]) throws {
        try write(hours.sorted { $0.hourStartMillis < $1.hourStartMillis })
    }

    private func write(_ hours: [PendingHour]) throws {
        let trimmed = hours.count > maxRows ? Array(hours.suffix(maxRows)) : hours
        let data = try JSONEncoder().encode(trimmed)
        try data.write(to: url, options: .atomic)
    }
}
