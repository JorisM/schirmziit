import AVFoundation
import SwiftUI

/// The camera, pointed at the square on the parent's screen.
///
/// The Android app has scanned in-app since pairing existed; an iPhone could
/// only go out to the system camera, come back through `schirmziit://enroll`,
/// and hope the child found the right app to open. This is the same act without
/// leaving the screen that is asking for the code.
///
/// It fills the form and closes. It never pairs: the code is one-shot, and a
/// screen that paired on arrival would spend it on a mis-scan — and the phone
/// still has no name until someone gives it one.
struct QrScannerSheet: View {
    let onFound: (EnrollLink) -> Void
    let onCancel: () -> Void
    /// Pinned instead of asked, for the goldens: a simulator has no camera, so
    /// the states worth an image are the two that are all text anyway.
    var pinned: ScanAccess?

    @State private var access: ScanAccess = .ask
    @State private var reader = ScanReader()
    /// A square that is not ours, said once — `ScanReader` is what keeps it from
    /// being said thirty times a second.
    @State private var stranger = false
    @Environment(\.openURL) private var openURL
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        NavigationStack {
            ZStack {
                Palette.paper.ignoresSafeArea()
                content
            }
            .navigationTitle(L("agent.pairing.scan.title"))
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(S("app.cancel"), action: onCancel)
                }
            }
        }
        .task {
            if let pinned {
                access = pinned
                return
            }
            access = .current()
            // Opening this sheet is the asking. A separate "allow the camera"
            // button in front of the system's own prompt is two prompts for one
            // decision.
            if access == .ask { access = await Self.request() }
        }
    }

    @ViewBuilder private var content: some View {
        switch access {
        case .ready:
            viewfinder
        case .ask:
            ProgressView().tint(Palette.accent)
        case .refused:
            // Settings, not a retry: once refused, `requestAccess` returns false
            // without showing anything, so a button that asks again looks broken.
            advice(L("agent.pairing.scan.refused"), settings: true)
        case .noCamera:
            advice(L("agent.pairing.scan.nocamera"), settings: false)
        }
    }

    private var viewfinder: some View {
        VStack(spacing: 16) {
            ZStack {
                CameraPreview(onPayload: saw)
                // A window rather than an instruction: the square goes in the
                // middle, and nothing has to say so.
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .strokeBorder(Palette.card.opacity(0.9), lineWidth: 3)
                    .padding(32)
            }
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .frame(maxWidth: .infinity)

            VStack(spacing: 6) {
                L("agent.pairing.scan.hint")
                    .font(.callout)
                    .foregroundStyle(Palette.inkMuted)
                if stranger {
                    L("agent.pairing.scan.notours")
                        .font(.callout)
                        .foregroundStyle(Palette.warn)
                }
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, alignment: .center)
            .motion(Motion.base, value: stranger)
        }
        .padding(20)
    }

    private func advice(_ text: Text, settings: Bool) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "camera.fill")
                .font(.largeTitle)
                .foregroundStyle(Palette.inkFaint)
            text
                .font(.callout)
                .foregroundStyle(Palette.inkMuted)
                .multilineTextAlignment(.center)
            if settings {
                // Bordered rather than the prominent glass action: the camera is
                // this screen's prominent action, and this is the way out of it
                // — the same weight `ErrorView` gives its retry. It also keeps
                // the state photographable, since Liquid Glass does not
                // composite off screen and takes the rest of the render with it.
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                } label: {
                    L("agent.pairing.scan.settings")
                }
                .buttonStyle(.bordered)
                .tint(Palette.accent)
            }
        }
        .padding(32)
    }

    /// One frame from the camera. `ScanReader` decides whether it is worth an
    /// answer; this only carries the answer to the screen.
    @MainActor private func saw(_ raw: String) {
        switch reader.read(raw) {
        case .enroll(let link):
            onFound(link)
        case .notOurs:
            stranger = true
        case .again:
            break
        }
    }

    private static func request() async -> ScanAccess {
        await AVCaptureDevice.requestAccess(for: .video) ? .ready : .refused
    }
}

/// The live camera, and the only part of this file no test can run: an
/// `AVCaptureSession` needs a camera, and a simulator has none. Everything that
/// could be decided elsewhere was — see `ScanReader`.
private struct CameraPreview: UIViewRepresentable {
    let onPayload: @MainActor (String) -> Void

    func makeCoordinator() -> ScannerSession {
        ScannerSession(onPayload: onPayload)
    }

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = context.coordinator.session
        view.previewLayer.videoGravity = .resizeAspectFill
        context.coordinator.start()
        return view
    }

    func updateUIView(_ view: PreviewView, context: Context) {}

    static func dismantleUIView(_ view: PreviewView, coordinator: ScannerSession) {
        // The camera light stays on until this happens.
        coordinator.stop()
    }
}

/// A view whose layer *is* the preview layer, so the camera image resizes with
/// the view instead of being resized after the fact on every layout pass.
private final class PreviewView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
}

/// `@unchecked Sendable` because `AVCaptureSession` is not `Sendable` and this
/// owns one: everything that touches it after construction happens on `queue`,
/// which is also the queue the metadata delegate is called on.
private final class ScannerSession: NSObject, AVCaptureMetadataOutputObjectsDelegate, @unchecked Sendable {
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "ch.jorisda.schirmziit.scanner")
    private let onPayload: @MainActor (String) -> Void

    init(onPayload: @escaping @MainActor (String) -> Void) {
        self.onPayload = onPayload
    }

    func start() {
        queue.async { [self] in
            if session.inputs.isEmpty { configure() }
            if !session.isRunning { session.startRunning() }
        }
    }

    func stop() {
        queue.async { [self] in
            if session.isRunning { session.stopRunning() }
        }
    }

    private func configure() {
        session.beginConfiguration()
        defer { session.commitConfiguration() }

        guard let camera = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: camera),
              session.canAddInput(input) else { return }
        session.addInput(input)

        let codes = AVCaptureMetadataOutput()
        guard session.canAddOutput(codes) else { return }
        session.addOutput(codes)
        codes.setMetadataObjectsDelegate(self, queue: queue)
        // After `addOutput`, never before: the available types are empty until
        // the output belongs to a session, and setting an unavailable type is a
        // crash rather than a no-op.
        codes.metadataObjectTypes = [.qr]
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput objects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard let code = objects.lazy
            .compactMap({ $0 as? AVMetadataMachineReadableCodeObject })
            .compactMap(\.stringValue)
            .first
        else { return }

        let deliver = onPayload
        Task { @MainActor in deliver(code) }
    }
}
