import Foundation

/// Every failure either role can be shown, as one value.
///
/// Built at a boundary — never in a view — so a screen cannot render an error
/// without a code to put on screen and a reference to report. The
/// `errorText: String?` this replaces could hold anything, and held the
/// server's English sentence.
struct AppError: Error, Identifiable, Sendable {
    let id = UUID()
    let code: ErrorCode
    let ref: String
    let at: Date
    /// A path, never a full URL. See `pathOnly`.
    let endpoint: String?
    let httpStatus: Int?

    init(
        code: ErrorCode,
        ref: String,
        endpoint: String?,
        httpStatus: Int? = nil,
        at: Date = Date()
    ) {
        self.code = code
        self.ref = ref
        self.at = at
        self.endpoint = endpoint.map(Self.pathOnly)
        self.httpStatus = httpStatus
        let entry = ErrorLog.Entry(
            code: code, ref: ref, at: at, endpoint: self.endpoint, httpStatus: httpStatus
        )
        ErrorLog.shared.record(entry)
    }

    init(problem: ApiProblem, endpoint: String?) {
        // Both fields are missing from a server older than the catalog, and an
        // unknown code means the server is newer than this app. Neither is worth
        // crashing or dropping the error over: the status still says what
        // happened, and a locally made reference still identifies the occurrence
        // in this device's own log.
        self.init(
            code: problem.code.flatMap(ErrorCode.init(wire:)) ?? .internal,
            ref: problem.ref ?? Self.makeRef(),
            endpoint: endpoint,
            httpStatus: problem.status
        )
    }

    /// A request that never produced a response.
    ///
    /// `URLError` distinguishes the cases a parent can act on differently — a
    /// tunnel, a slow server, a certificate, a wrong address. A browser reports
    /// all four as one opaque failure, which is why the dashboard has no TLS
    /// code and this does.
    static func transport(_ error: Error, endpoint: String?) -> AppError {
        let code: ErrorCode
        switch (error as? URLError)?.code {
        case .some(.notConnectedToInternet), .some(.networkConnectionLost),
             .some(.dataNotAllowed), .some(.internationalRoamingOff):
            code = .offline
        case .some(.timedOut):
            code = .timeout
        case .some(.secureConnectionFailed), .some(.serverCertificateUntrusted),
             .some(.serverCertificateHasBadDate), .some(.serverCertificateNotYetValid),
             .some(.serverCertificateHasUnknownRoot):
            code = .tlsFailed
        default:
            code = .serverUnreachable
        }
        return AppError(code: code, ref: makeRef(), endpoint: endpoint)
    }

    /// Something answered in the server's place — a guest-network login page, a
    /// proxy error page. It must throw rather than be read as anything else.
    static func badResponseBody(endpoint: String?, httpStatus: Int?) -> AppError {
        AppError(code: .badResponseBody, ref: makeRef(), endpoint: endpoint, httpStatus: httpStatus)
    }

    /// A keychain read, a decode, a permission — anything that failed on the
    /// phone itself and never touched the network.
    static func local(_ code: ErrorCode, endpoint: String? = nil) -> AppError {
        AppError(code: code, ref: makeRef(), endpoint: endpoint)
    }

    private static func makeRef() -> String {
        (0..<3).map { _ in String(format: "%02x", UInt8.random(in: 0...255)) }.joined()
    }

    /// A self-hoster pasting a screenshot into a public issue must not publish
    /// the address of the machine in their flat.
    private static func pathOnly(_ endpoint: String) -> String {
        guard let path = URLComponents(string: endpoint)?.path, !path.isEmpty else { return endpoint }
        return path
    }
}

extension AppError {
    /// The block behind "copy details": what a maintainer needs and nothing that
    /// describes a family — no email, no child name, no request or response
    /// body, and the endpoint as a path.
    var copyDetails: String {
        var lines = [
            "\(code.wire) · \(ref)",
            Self.stamp.string(from: at),
            "schirmziit \(Self.appVersion) · ios \(Self.systemVersion) · \(Self.deviceModel)",
        ]
        if let endpoint {
            let status = httpStatus.map { " → \($0)" } ?? ""
            lines.append("GET \(endpoint)\(status)")
        }
        return lines.joined(separator: "\n")
    }

    private static let stamp: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss ZZZZZ"
        return formatter
    }()

    private static var appVersion: String {
        Bundle.schirmziitKit.infoDictionary?["CFBundleShortVersionString"] as? String ?? "dev"
    }

    private static var systemVersion: String {
        let version = ProcessInfo.processInfo.operatingSystemVersion
        return "\(version.majorVersion).\(version.minorVersion)"
    }

    /// `iPhone15,2`, not "iPhone" — the difference between "a layout bug" and "a
    /// layout bug on the small phone".
    private static var deviceModel: String {
        var info = utsname()
        uname(&info)
        return withUnsafePointer(to: &info.machine) { pointer in
            pointer.withMemoryRebound(to: CChar.self, capacity: 1) { String(cString: $0) }
        }
    }
}

/// Fifty entries, in memory.
///
/// The screenshot is the report and "copy details" is used in the moment;
/// persisting a log of a family's failures across launches would be storing
/// data nobody asked for.
///
/// A lock rather than an actor, deliberately. Recording happens inside
/// `AppError.init`, which is not async, so an actor forces the write into a
/// detached `Task` — and then the entries land in whatever order the scheduler
/// picks, arriving after the error they describe is already on screen. The
/// "before this:" list would be out of order, or short, exactly when someone is
/// reading it to work out what has been failing all morning.
final class ErrorLog: @unchecked Sendable {
    static let shared = ErrorLog()

    /// A plain snapshot rather than the `AppError` itself: recording happens in
    /// `init`, where `self` is not yet available to hand anywhere.
    struct Entry: Sendable, Equatable {
        let code: ErrorCode
        let ref: String
        let at: Date
        let endpoint: String?
        let httpStatus: Int?
    }

    private let lock = NSLock()
    private var entries: [Entry] = []
    private let limit = 50

    func record(_ entry: Entry) {
        lock.lock()
        defer { lock.unlock() }
        entries.append(entry)
        if entries.count > limit { entries.removeFirst(entries.count - limit) }
    }

    func recent() -> [Entry] {
        lock.lock()
        defer { lock.unlock() }
        return entries
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        entries = []
    }
}
