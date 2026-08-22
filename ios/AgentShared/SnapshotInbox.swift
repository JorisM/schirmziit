import Foundation

/// One file per hour, written by the extension and drained by the app.
///
/// A file per hour rather than one shared file: the extension and the app run in
/// different processes with no lock between them, and a half-written array would
/// lose a day. A whole file either arrives or it doesn't.
struct SnapshotInbox: Sendable {
    let directory: URL
    private let prefix = "snapshot-"

    init(directory: URL = GroupContainer.directory()) {
        self.directory = directory
    }

    func write(_ snapshot: UsageSnapshot) throws {
        let url = directory.appendingPathComponent("\(prefix)\(snapshot.hourStartMillis).json")
        try JSONEncoder().encode(snapshot).write(to: url, options: .atomic)
    }

    func drain() -> [UsageSnapshot] {
        let files = (try? FileManager.default.contentsOfDirectory(at: directory,
                                                                 includingPropertiesForKeys: nil)) ?? []
        var snapshots: [UsageSnapshot] = []
        for file in files.sorted(by: { $0.lastPathComponent < $1.lastPathComponent })
        where file.lastPathComponent.hasPrefix(prefix) {
            guard let data = try? Data(contentsOf: file),
                  let snapshot = try? JSONDecoder().decode(UsageSnapshot.self, from: data) else {
                // Unreadable file: drop it rather than retrying forever. Losing
                // one hour beats a queue that never drains.
                try? FileManager.default.removeItem(at: file)
                continue
            }
            snapshots.append(snapshot)
            try? FileManager.default.removeItem(at: file)
        }
        return snapshots
    }
}
