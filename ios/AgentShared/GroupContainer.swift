import Foundation

/// Where the app and its extensions meet on disk.
///
/// The App Group container needs a provisioning profile that grants it. A local
/// build signed with a free account has none, so this falls back to the app's
/// own Application Support directory: the app still runs and syncs, it just
/// cannot see what an extension wrote. Failing hard here would make every
/// unsigned build unusable for development.
enum GroupContainer {
    static let identifier = "group.ch.jorisda.schirmziit"

    static func directory(
        groupIdentifier: String = identifier,
        fileManager: FileManager = .default
    ) -> URL {
        let base = fileManager.containerURL(forSecurityApplicationGroupIdentifier: groupIdentifier)
            ?? fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory

        let directory = base.appendingPathComponent("schirmziit", isDirectory: true)
        try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    /// True when the real shared container is available, i.e. extension data can
    /// actually reach the app. The status screen says so plainly instead of
    /// pretending everything is fine.
    static func isShared(
        groupIdentifier: String = identifier,
        fileManager: FileManager = .default
    ) -> Bool {
        fileManager.containerURL(forSecurityApplicationGroupIdentifier: groupIdentifier) != nil
    }
}
