import AVFoundation
import Foundation

/// Whether this phone can be pointed at a pairing square, and what to say when
/// it cannot.
///
/// Four answers rather than a `Bool`, because the three ways of failing want
/// three different sentences: one is a question the system will ask, one is a
/// switch in Settings, and one is a phone that simply has no camera. Telling a
/// child to grant access on a device that has no camera sends them looking for a
/// switch that is not there.
public enum ScanAccess: Equatable, Sendable {
    /// Granted. The preview can start.
    case ready
    /// Not asked yet — opening the sheet is the asking.
    case ask
    /// Refused, or forbidden by Screen Time or a managed profile. Nothing this
    /// app asks will change it, so the sheet points at Settings and leaves the
    /// typed code as the way through.
    case refused
    /// No camera at all: a simulator, or an iPad without one.
    case noCamera

    public static func of(_ status: AVAuthorizationStatus, hasCamera: Bool) -> ScanAccess {
        // Ahead of the status on purpose: a phone with no camera is never asked
        // for permission, so it can report `.authorized` and still have nothing
        // to show.
        guard hasCamera else { return .noCamera }
        switch status {
        case .authorized: return .ready
        case .notDetermined: return .ask
        default: return .refused
        }
    }

    /// What the system currently says, on this phone.
    public static func current(
        status: AVAuthorizationStatus = AVCaptureDevice.authorizationStatus(for: .video),
        hasCamera: Bool = AVCaptureDevice.default(for: .video) != nil
    ) -> ScanAccess {
        of(status, hasCamera: hasCamera)
    }
}

/// What a frame from the camera meant.
public enum ScanRead: Equatable, Sendable {
    /// A pairing square. Fill the form with it — never submit it: the code is
    /// one-shot, and a screen that pairs on arrival spends it on a mis-scan.
    case enroll(EnrollLink)
    /// A square, but somebody else's. Worth saying so once.
    case notOurs
    /// The same thing again, still in front of the lens. Say nothing.
    case again
}

/// The camera, reduced to a decision.
///
/// A camera is not a button. It reports whatever is in frame for every frame it
/// captures — thirty times a second, for as long as someone holds the square
/// up — so without a memory the screen answers a single square thirty times a
/// second: a message that flickers, or a form filled again after the sheet that
/// filled it has closed.
///
/// This lives apart from the view because it is the half that can be tested: an
/// `AVCaptureSession` needs a camera, and there is none in a simulator.
public struct ScanReader: Sendable {
    /// Set once a pairing square has been read. Everything after it is the same
    /// scan still happening.
    private var done = false
    /// The last thing refused, so refusing it again is silent while a *different*
    /// wrong square still gets an answer — a new square in front of the lens is
    /// a new attempt by whoever is holding the phone.
    private var refused: String?

    public init() {}

    public mutating func read(_ raw: String) -> ScanRead {
        guard !done else { return .again }

        if let link = EnrollLink(raw) {
            done = true
            return .enroll(link)
        }

        guard refused != raw else { return .again }
        refused = raw
        return .notOurs
    }
}
