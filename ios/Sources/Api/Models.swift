import Foundation

/// Mirrors of the server's response types. Hand-written on purpose: the parent
/// read endpoints are not part of `core::wire`, so there is nothing to generate
/// from. `ContractTests` decodes captured responses to catch drift.
struct MeResponse: Codable, Sendable {
    let id: String
    let email: String
    let familyId: String
}

struct ChildResponse: Codable, Sendable, Identifiable {
    let id: String
    let displayName: String
    let todayMs: Int64
}

/// The one-shot code a child's phone is enrolled with.
///
/// `qrPayload` is `schirmziit://enroll?url=…&code=…`, meant for a camera: it
/// carries the server address as well as the code, which is the half of the
/// pairing whose failure is silent. `PairDeviceView.serverAddress` reads the
/// address back out of it for the parent to type.
struct EnrollmentResponse: Codable, Sendable {
    let code: String
    let expiresAt: Date
    let qrPayload: String
    /// `qrPayload` already drawn by the server, or nil when it could not draw
    /// it. Nothing in this app encodes a QR: one encoder in `crates/core` is
    /// what keeps this phone, the dashboard and an Android phone from handing a
    /// family three different squares.
    let qr: QrMatrix?
}

/// A square of modules, row by row, `1` dark and `0` light, quiet zone
/// included — exactly as the server sent it.
struct QrMatrix: Codable, Sendable, Equatable {
    let size: Int
    let rows: [String]

    /// False for anything that is not genuinely a square of `size` binary
    /// modules. A truncated or ragged matrix draws a square that scans as
    /// nothing, which a parent reads as their camera being at fault — and the
    /// code and address beside it pair the phone without it.
    var isDrawable: Bool {
        size > 0
            && rows.count == size
            && rows.allSatisfy { row in
                row.count == size && row.allSatisfy { $0 == "0" || $0 == "1" }
            }
    }

    func isDark(x: Int, y: Int) -> Bool {
        let row = rows[y]
        return row[row.index(row.startIndex, offsetBy: x)] == "1"
    }
}

/// What a purge actually removed, straight from the server's `rows_affected`.
///
/// Counted rather than asserted: "deleted" with nothing behind it is exactly the
/// claim a family has no way to check, and a delete that matched nothing has to
/// be able to say zero instead of implying a purge.
///
/// Non-optional on purpose. A body missing a count is not a purge of zero rows,
/// it is a body this app cannot read — and decoding it leniently would show a
/// parent a receipt for a deletion that may never have happened.
struct PurgeResponse: Codable, Sendable, Equatable {
    let deletedUsageHours: Int
    let deletedDeviceHours: Int
    let deletedUsageDays: Int
}

struct DeviceStatus: Codable, Sendable, Identifiable {
    let id: String
    let label: String
    let lastSeenAt: Date?
    let stale: Bool
}

struct UsagePoint: Codable, Sendable {
    /// RFC3339 for hourly buckets, `YYYY-MM-DD` for daily ones.
    let start: String
    let foregroundMs: Int
    let launchCount: Int
    /// Media playing with the screen off. A separate measure from screen time,
    /// never a part of `foregroundMs` and never added to it.
    let backgroundMs: Int
}

struct UsageSeries: Codable, Sendable, Identifiable {
    let package: String
    let label: String
    let points: [UsagePoint]

    var id: String { package }
    var totalMs: Int { points.reduce(0) { $0 + $1.foregroundMs } }
    var launches: Int { points.reduce(0) { $0 + $1.launchCount } }
    var backgroundMs: Int { points.reduce(0) { $0 + $1.backgroundMs } }
}

struct DeviceTotal: Codable, Sendable {
    let start: String
    let screenOnMs: Int
    let unlockCount: Int
    /// False means no device reporting this bucket could observe background
    /// playback — an iPhone, or an Android phone whose family declined the
    /// grant. It never means nothing played.
    let backgroundMeasured: Bool
}

struct UsageResponse: Codable, Sendable {
    let childId: String
    let from: String
    let to: String
    let bucket: String
    let tz: String
    let devices: [DeviceStatus]
    let series: [UsageSeries]
    let deviceTotals: [DeviceTotal]

    var screenTimeMs: Int { series.reduce(0) { $0 + $1.totalMs } }
    var unlocks: Int { deviceTotals.reduce(0) { $0 + $1.unlockCount } }
    /// Kept apart from `screenTimeMs` on purpose: adding the two would count an
    /// audiobook heard with the screen off as time spent looking at a phone.
    var backgroundMs: Int { series.reduce(0) { $0 + $1.backgroundMs } }
    /// One device that could observe it is enough for the number to mean
    /// something; all of them blind is what makes it unknown.
    var backgroundMeasured: Bool { deviceTotals.contains { $0.backgroundMeasured } }
}

/// One app in both weeks. `deltaMs` may be negative; a mover moves in either
/// direction and the view says which.
struct AppMove: Codable, Sendable, Equatable, Identifiable {
    let package: String
    let label: String
    let foregroundMs: Int
    let previousForegroundMs: Int

    var id: String { package }
    var deltaMs: Int { foregroundMs - previousForegroundMs }
}

/// Seven complete days against the seven before them, as the server compared
/// them. Nothing here is recomputed on the phone.
struct WeekComparison: Codable, Sendable, Equatable {
    let from: String
    let to: String
    let previousFrom: String
    let previousTo: String
    let totalMs: Int
    let previousTotalMs: Int
    /// From `eveningFromHour` to local midnight — a subset of `totalMs`, and
    /// never something to add to it.
    let eveningMs: Int
    let previousEveningMs: Int
    let eveningFromHour: Int
    let movers: [AppMove]
    /// False when no phone reported the earlier week at all. A week against
    /// silence is a first week, not a doubling, and the view has to say so.
    let previousMeasured: Bool

    var deltaMs: Int { totalMs - previousTotalMs }
    var eveningDeltaMs: Int { eveningMs - previousEveningMs }
}

struct InsightResponse: Codable, Sendable, Equatable {
    let childId: String
    let tz: String
    let week: WeekComparison
}

/// RFC 9457 problem+json, as the server sends it.
struct ApiProblem: Codable, Sendable, Error {
    let type: String
    let title: String
    let status: Int
    /// English, for the server log and the copy-details block. Never rendered:
    /// the app speaks four languages and looks its copy up by `code`.
    let detail: String
    /// The catalog code, e.g. `SZ-E201`.
    ///
    /// Optional because a self-hoster upgrades their server on their own
    /// schedule: an app newer than the server it talks to is a normal state for
    /// this product, and a server from before the catalog shipped sends neither
    /// this nor `ref`. Requiring them would fail decoding and report a healthy
    /// old server as "that answer didn't come from your server".
    let code: String?
    /// Six hex characters, the head of the server's request id.
    let ref: String?
}
