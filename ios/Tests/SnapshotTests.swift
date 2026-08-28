import SnapshotTesting
import SwiftUI
import XCTest
@testable import SchirmziitKit

/// Images of every screen, in light and dark, plus the help text in all four
/// languages — where a translation is long enough to break a layout.
///
/// These are the tests that catch what unit tests structurally cannot: a card
/// that overflows, a colour that vanishes on the dark surface, a German sentence
/// that pushes a button off screen. Re-record deliberately with
/// `RECORD_SNAPSHOTS=1`, never to make a red test go green.
///
/// Two limitations to read these images with. Liquid Glass does not composite in
/// an off-screen render, so a `.glassProminent` button or any `glassEffect`
/// surface comes out as a blank shape here while looking correct on a device —
/// judge those on the phone, not in the golden. And: `locale` sets the SwiftUI
/// environment, so
/// everything drawn through `L(…)` follows it. Text-field placeholders go through
/// `String(localized:)`, which reads the *process* locale — so they appear in
/// English here while being correct on a German phone.
@MainActor
final class SnapshotTests: XCTestCase {
    /// Missing images are written, existing ones are compared. Deliberately not
    /// driven by an environment flag: `xcodebuild` does not pass plain variables
    /// into the test process, so a record flag appears to work while doing
    /// nothing — the images only ever changed because they were absent. To
    /// re-record, delete them (`just ios-record`), which is a visible act in the
    /// diff rather than a flag someone leaves on.
    private let recordMode: SnapshotTestingConfiguration.Record = .missing

    private func assert<V: View>(
        _ view: V,
        named name: String,
        locale: String = "de",
        disablesAnimations: Bool = true,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        for style in [UIUserInterfaceStyle.light, .dark] {
            let traits = UITraitCollection(userInterfaceStyle: style)
            let wrapped = view
                .environment(\.locale, Locale(identifier: locale))
                .frame(width: 393, height: 852)
                // The offscreen host has no live display link, so a real
                // `.animation(...)` transition (the ribbon fill, the strip's
                // and app rows' entry stagger) commits its `onAppear`-triggered
                // state change but never advances past frame zero of the
                // interpolation — captured as invisible content, not settled
                // content, no matter how long `.wait` below runs for. Disabling
                // transactions for the capture is what reduced motion already
                // gets for free (`Motion.animation` returns `nil`): the target
                // value applies instantly, so every screen's still image shows
                // what a user sees once the motion has finished, which is the
                // only thing a still image can faithfully represent anyway.
                // `disablesAnimations` is a parameter, not a constant `true`,
                // because it would otherwise also force the reduced-motion
                // test's transaction to settle instantly regardless of what
                // `DayRibbonView` actually does with `accessibilityReduceMotion`
                // — turning that test into one that only ever matches itself.
                .transaction { $0.disablesAnimations = disablesAnimations }
            withSnapshotTesting(record: recordMode) {
            assertSnapshot(
                of: wrapped,
                // Exact-pixel comparison is not stable across runs on the same
                // simulator: text antialiasing differs by a hair. The tolerance
                // is tight enough that a moved control or a changed colour still
                // fails, and loose enough that nothing fails for nothing.
                // The wait is not padding: a NavigationStack lays its bar out
                // asynchronously, so without it the large title is caught
                // mid-settle and every run differs from the last.
                as: .wait(
                    for: 0.5,
                    on: .image(
                        precision: 0.99,
                        perceptualPrecision: 0.98,
                        layout: .device(config: .iPhone13Pro),
                        traits: traits
                    )
                ),
                named: "\(name)-\(locale)-\(style == .light ? "light" : "dark")",
                file: file,
                testName: "screens",
                line: line
            )
            }
        }
    }

    // MARK: - The first question the app asks

    func testRoleChoice() {
        assert(RoleChoiceView(onParent: {}, onChild: {}), named: "role-choice")
    }

    // MARK: - Errors

    /// The panel a parent photographs. Four states, because each one is a
    /// different promise: an urgent failure, an expected one that must not be
    /// painted red, the detail a report is copied from, and the banner that
    /// leaves loaded data on screen.
    private func failure(_ code: ErrorCode, ref: String = "7f3a9c") -> AppError {
        AppError(code: code, ref: ref, endpoint: "/v1/children", httpStatus: 500)
    }

    /// Snapshotted inside a list, because that is where every one of these
    /// actually renders — a bare view would show the panel hard against the
    /// screen edge and hide whether the insets are right.
    private func listed<V: View>(_ view: V) -> some View {
        List { Section { view } }.schirmziitList()
    }

    func testErrorInline() {
        assert(listed(ErrorView(error: failure(.internal), onRetry: {})), named: "error-inline")
    }

    /// Offline is expected and self-correcting. If this image is red, the
    /// colour that means "something actually broke" has been spent.
    func testErrorNeutral() {
        assert(listed(ErrorView(error: failure(.offline))), named: "error-neutral")
    }

    func testErrorBanner() {
        assert(
            listed(ErrorView(error: failure(.internal), placement: .banner, onRetry: {})),
            named: "error-banner"
        )
    }

    // MARK: - Child mode

    private func agent(
        credentials: AgentCredentials? = nil,
        authorization: ScreenTimeAuthorization = .approved,
        role: AppRole? = .child,
        transport: Transport = StubTransport(status: 200, body: "{}")
    ) -> AgentModel {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return AgentModel(
            store: FileHourStore(directory: directory),
            inbox: SnapshotInbox(directory: directory),
            credentials: InMemoryCredentialStore(credentials),
            transport: transport,
            authorizer: StubAuthorizer(state: authorization),
            monitoring: SpyMonitoring(),
            roles: InMemoryRoleStore(role)
        )
    }

    /// `AgentMyTimeView` loads itself in `.task`, and a snapshot can mount a
    /// view more than once (once per light/dark render). Answering by query
    /// shape rather than call order keeps every mount fed the same data
    /// instead of the second one draining a fixed reply queue and showing a
    /// spurious error banner.
    private struct MyTimeStub: Transport {
        let stripBody: String
        let dayBody: String

        func send(_ request: HttpRequest) async throws -> HttpResponse {
            let isStrip = request.url.absoluteString.contains("bucket=day")
            return HttpResponse(status: 200, body: Data((isStrip ? stripBody : dayBody).utf8))
        }
    }

    private var paired: AgentCredentials {
        AgentCredentials(
            baseURL: URL(string: "https://api.schirmziit.ch")!,
            deviceId: "dev-1",
            token: "tok-1",
            parentEmail: "anna@example.ch"
        )
    }

    func testChildModeReporting() {
        assert(AgentRootView(model: agent(credentials: paired)), named: "child-reporting")
    }

    func testChildModeNeedsScreenTimeAccess() {
        assert(
            AgentRootView(model: agent(credentials: paired, authorization: .notDetermined)),
            named: "child-needs-permission"
        )
    }

    /// The state every build without Apple's entitlement is in. It has to read as
    /// "this cannot work yet", not as a bug.
    func testChildModeScreenTimeUnavailable() {
        assert(
            AgentRootView(
                model: agent(credentials: paired, authorization: .unavailable("no entitlement"))
            ),
            named: "child-unavailable"
        )
    }

    func testChildSetup() {
        assert(ChildSetupView(model: agent(role: nil), onCancel: {}), named: "child-setup")
    }

    func testChildHelp() {
        assert(AgentHelpView(), named: "child-help")
    }

    func testMyTime() async {
        // Fixed figures, the same fortnight `testDayStrip` uses, so the strip
        // reads identically whether a parent or a child is looking at it.
        let stripBody = """
        {"from":"2026-08-11","to":"2026-08-24","bucket":"day","series":[
            {"package":"com.games.puzzle","label":"Puzzle","points":[
                {"start":"2026-08-11","foreground_ms":2400000,"launch_count":3},
                {"start":"2026-08-12","foreground_ms":3300000,"launch_count":4},
                {"start":"2026-08-14","foreground_ms":720000,"launch_count":1},
                {"start":"2026-08-15","foreground_ms":5400000,"launch_count":6},
                {"start":"2026-08-16","foreground_ms":3900000,"launch_count":5},
                {"start":"2026-08-17","foreground_ms":1800000,"launch_count":2},
                {"start":"2026-08-18","foreground_ms":2700000,"launch_count":3},
                {"start":"2026-08-19","foreground_ms":1200000,"launch_count":2},
                {"start":"2026-08-20","foreground_ms":6000000,"launch_count":7},
                {"start":"2026-08-21","foreground_ms":900000,"launch_count":1},
                {"start":"2026-08-22","foreground_ms":3600000,"launch_count":4},
                {"start":"2026-08-23","foreground_ms":2100000,"launch_count":3},
                {"start":"2026-08-24","foreground_ms":3000000,"launch_count":4}
            ]}
        ],"device_totals":[]}
        """
        // Fixed figures, the same shape `testDayRibbon` uses.
        let dayBody = """
        {"from":"2026-08-24","to":"2026-08-24","bucket":"hour","series":[
            {"package":"com.games.puzzle","label":"Puzzle","points":[
                {"start":"2026-08-24T13:00:00+02:00","foreground_ms":3000000,"launch_count":6}]},
            {"package":"com.chat.messenger","label":"Messenger","points":[
                {"start":"2026-08-24T18:00:00+02:00","foreground_ms":1800000,"launch_count":9}]},
            {"package":"com.video.stream","label":"StreamTV","points":[
                {"start":"2026-08-24T19:00:00+02:00","foreground_ms":1200000,"launch_count":2}]},
            {"package":"com.social.feed","label":"Feed","points":[
                {"start":"2026-08-24T08:00:00+02:00","foreground_ms":600000,"launch_count":5}]},
            {"package":"com.music.player","label":"Music","points":[
                {"start":"2026-08-24T20:00:00+02:00","foreground_ms":300000,"launch_count":1}]},
            {"package":"com.browser","label":"Browser","points":[
                {"start":"2026-08-24T21:00:00+02:00","foreground_ms":120000,"launch_count":1}]}
        ],"device_totals":[
            {"start":"2026-08-24T07:00:00+02:00","screen_on_ms":720000,"unlock_count":3},
            {"start":"2026-08-24T08:00:00+02:00","screen_on_ms":2040000,"unlock_count":3},
            {"start":"2026-08-24T09:00:00+02:00","screen_on_ms":480000,"unlock_count":3},
            {"start":"2026-08-24T12:00:00+02:00","screen_on_ms":2460000,"unlock_count":3},
            {"start":"2026-08-24T13:00:00+02:00","screen_on_ms":3300000,"unlock_count":3},
            {"start":"2026-08-24T14:00:00+02:00","screen_on_ms":1320000,"unlock_count":3},
            {"start":"2026-08-24T15:00:00+02:00","screen_on_ms":360000,"unlock_count":3},
            {"start":"2026-08-24T18:00:00+02:00","screen_on_ms":3780000,"unlock_count":3},
            {"start":"2026-08-24T19:00:00+02:00","screen_on_ms":2880000,"unlock_count":3},
            {"start":"2026-08-24T20:00:00+02:00","screen_on_ms":1140000,"unlock_count":3},
            {"start":"2026-08-24T21:00:00+02:00","screen_on_ms":240000,"unlock_count":3}
        ]}
        """
        let model = agent(credentials: paired, transport: MyTimeStub(stripBody: stripBody, dayBody: dayBody))
        // Loaded ahead of the render rather than left to the view's own
        // `.task`: the snapshot host does not reliably run that to completion
        // inside the settle wait, and a view that mounts twice (light, dark)
        // must show the real numbers both times, not a spinner half of the time.
        await model.loadMyTimeStrip()
        // An explicit day, not model.mySelectedDay (today's real date):
        // the fixture above is dated 2026-08-24, and comparing against the
        // wall clock meant no bar drew its selection outline from 2026-08-25
        // on — the diff stayed under tolerance and the test stopped proving
        // the selection renders at all.
        await model.selectMyDay("2026-08-24")
        assert(AgentMyTimeView(model: model), named: "my-time")
    }

    /// The child's own list folds sub-minute glances exactly as the parent's
    /// `AppRowsView` does: two glances behind the disclosure, and one that
    /// rounds to 0 s dropped from the image entirely — a child and a parent
    /// must never be shown a different list.
    func testMyTimeFoldsTheSubMinuteApps() async {
        let stripBody = """
        {"from":"2026-08-11","to":"2026-08-24","bucket":"day","series":[
            {"package":"com.games.puzzle","label":"Puzzle","points":[
                {"start":"2026-08-24","foreground_ms":3000000,"launch_count":4}
            ]}
        ],"device_totals":[]}
        """
        let dayBody = """
        {"from":"2026-08-24","to":"2026-08-24","bucket":"hour","series":[
            {"package":"com.games.puzzle","label":"Puzzle","points":[
                {"start":"2026-08-24T13:00:00+02:00","foreground_ms":3000000,"launch_count":6}]},
            {"package":"com.utility.check","label":"QuickCheck","points":[
                {"start":"2026-08-24T14:00:00+02:00","foreground_ms":45000,"launch_count":1}]},
            {"package":"com.weather","label":"Weather","points":[
                {"start":"2026-08-24T15:00:00+02:00","foreground_ms":20000,"launch_count":1}]},
            {"package":"com.system.blink","label":"Blink","points":[
                {"start":"2026-08-24T16:00:00+02:00","foreground_ms":300,"launch_count":1}]}
        ],"device_totals":[
            {"start":"2026-08-24T13:00:00+02:00","screen_on_ms":3000000,"unlock_count":3}
        ]}
        """
        let model = agent(credentials: paired, transport: MyTimeStub(stripBody: stripBody, dayBody: dayBody))
        await model.loadMyTimeStrip()
        // An explicit day, not model.mySelectedDay (today's real date):
        // the fixture above is dated 2026-08-24, and comparing against the
        // wall clock meant no bar drew its selection outline from 2026-08-25
        // on — the diff stayed under tolerance and the test stopped proving
        // the selection renders at all.
        await model.selectMyDay("2026-08-24")
        assert(AgentMyTimeView(model: model), named: "my-time-folded")
    }

    // MARK: - Parent mode

    func testSignIn() {
        assert(SignInView(client: ApiClient(), onSignedIn: { _ in }), named: "sign-in")
    }

    /// The first screen after signing in, and until now the only parent screen
    /// with no golden at all — which is why the Add control it grew could have
    /// landed on top of a row or off the bar without a test noticing. `children`
    /// is passed in rather than fetched for the reason it is non-private: the
    /// snapshot host does not reliably finish this view's `.task`.
    ///
    /// Reduced motion, not `disablesAnimations`: the totals count up inside a
    /// `TimelineView(.animation)`, which a suppressed transaction does not stop
    /// — the first recording of this image caught 55 min of a 2 h 14 min total
    /// and would have differed on every run. `CountingTotal` renders the target
    /// straight away under reduced motion, and the settled numbers are the only
    /// thing a still image of a count-up can honestly assert. The final layout
    /// is the same view either way (`CountingTotal.label`).
    func testChildrenList() {
        assert(
            ChildrenView(
                client: ApiClient(),
                onSignOut: {},
                children: [
                    ChildResponse(id: "a", displayName: "Mira", todayMs: 8_040_000),
                    ChildResponse(id: "b", displayName: "Jonas", todayMs: 2_700_000),
                ]
            )
            .environment(\._accessibilityReduceMotion, true),
            named: "children"
        )
    }

    /// The state the Add control exists for. An empty list has to invite the
    /// action, not merely report the absence.
    func testChildrenListWhenEmpty() {
        assert(
            ChildrenView(client: ApiClient(), onSignOut: {}, children: []),
            named: "children-empty"
        )
    }

    /// The card a parent reads six characters off, and the state it turns into
    /// fifteen minutes later.
    ///
    /// Fixed instants and an explicit zone: `Text(date, format:)` follows the
    /// environment, so a wall-clock expiry would render a different time on
    /// every run and on every machine. The far-future one is what keeps the
    /// "valid until" image from expiring on its own some morning.
    private func pairing(expiresAt: Double, expired: Bool, qr: QrMatrix? = pairingSquare()) -> some View {
        List {
            PairDeviceView(
                client: ApiClient(),
                childId: "kid",
                enrollment: EnrollmentResponse(
                    code: "K7MNPQ",
                    expiresAt: Date(timeIntervalSince1970: expiresAt),
                    qrPayload: "schirmziit://enroll?url=https://api.schirmziit.ch&code=K7MNPQ",
                    qr: qr
                ),
                expired: expired
            )
        }
        .schirmziitList()
        .environment(\.timeZone, TimeZone(identifier: "Europe/Zurich")!)
    }

    /// The real version-4 square `crates/core`'s test pins, carried into this
    /// bundle as a resource. It encodes the same address and the same code
    /// shown beside it, so scanning one of these goldens gives back the card
    /// being looked at rather than a fixture from somewhere else.
    private static func pairingSquare() -> QrMatrix {
        let url = Bundle(for: SnapshotTests.self).url(forResource: "enroll_qr", withExtension: "txt")
        let text = (try? String(contentsOf: url!, encoding: .utf8)) ?? ""
        let rows = text.split(separator: "\n").map { line in
            String(line.map { $0 == "#" ? "1" : "0" })
        }
        return QrMatrix(size: rows.count, rows: rows)
    }

    func testPairDevice() {
        assert(pairing(expiresAt: 4_091_498_100, expired: false), named: "pair-device")
    }

    /// A server that drew no square — an older one, or one whose public URL is
    /// too long to encode. What must not appear is an empty frame: the code and
    /// the address pair the phone exactly as they did before the square
    /// existed.
    func testPairDeviceWithoutASquare() {
        assert(
            pairing(expiresAt: 4_091_498_100, expired: false, qr: nil),
            named: "pair-device-no-square"
        )
    }

    /// An expired code shown as usable sends a parent to a phone that refuses
    /// it, so this is a different image, not a restyled line of the one above.
    func testPairDeviceExpired() {
        assert(pairing(expiresAt: 1_787_814_900, expired: true), named: "pair-device-expired")
    }

    /// The control that deletes a child's stored figures, and the receipt it
    /// turns into.
    ///
    /// Two images, because they are two different claims. The resting state has
    /// to read as one quiet destructive button under a sentence saying what it
    /// does — one press must not delete — and the receipt has to show the
    /// server's own counts rather than the word "deleted" on its own.
    private func purge(purged: PurgeResponse?) -> some View {
        List {
            PurgeDataView(client: ApiClient(), childId: "kid", onPurged: {}, purged: purged)
        }
        .schirmziitList()
    }

    func testPurgeData() {
        assert(purge(purged: nil), named: "purge-data")
    }

    func testPurgeDataReceipt() {
        assert(
            purge(
                purged: PurgeResponse(
                    deletedUsageHours: 412,
                    deletedDeviceHours: 168,
                    deletedUsageDays: 14
                )
            ),
            named: "purge-data-done"
        )
    }

    /// A purge that matched nothing. Zero has to be legible as an answer, or a
    /// parent cannot tell a purge that worked from one that found nothing.
    func testPurgeDataReceiptWithNothingToDelete() {
        assert(
            purge(
                purged: PurgeResponse(deletedUsageHours: 0, deletedDeviceHours: 0, deletedUsageDays: 0)
            ),
            named: "purge-data-zero"
        )
    }

    func testDayRibbon() {
        // Fixed figures: a ribbon that changes shape between runs is not a
        // snapshot test, it is a random image generator.
        let minutes = [0, 0, 0, 0, 0, 0, 0, 12, 34, 8, 0, 0, 41, 55, 22, 6, 0, 0, 63, 48, 19, 4, 0, 0]
        let totals = minutes.enumerated().map { hour, value in
            DeviceTotal(
                start: String(format: "2026-08-22T%02d:00:00+02:00", hour),
                screenOnMs: value * 60_000,
                unlockCount: value > 0 ? 3 : 0
            )
        }
        assert(DayRibbonView(totals: totals).padding(), named: "day-ribbon")
    }

    /// The reduced-motion path must land on the finished layout on frame one.
    ///
    /// Deliberately asserted against `day-ribbon` — the reference the normal
    /// render already produced. A reduced-motion capture with its own name would
    /// only ever prove it matches itself; sharing the name is what makes this a
    /// test that a screen is not animating when it was asked not to.
    func testDayRibbonUnderReducedMotionIsTheSettledImage() {
        let minutes = [0, 0, 0, 0, 0, 0, 0, 12, 34, 8, 0, 0, 41, 55, 22, 6, 0, 0, 63, 48, 19, 4, 0, 0]
        let totals = minutes.enumerated().map { hour, value in
            DeviceTotal(
                start: String(format: "2026-08-22T%02d:00:00+02:00", hour),
                screenOnMs: value * 60_000,
                unlockCount: value > 0 ? 3 : 0
            )
        }
        assert(
            DayRibbonView(totals: totals)
                .padding()
                // `\.accessibilityReduceMotion` is get-only on this SDK — it mirrors
                // the system setting, not something a test can inject. The
                // underscored sibling is the actual writable storage SwiftUI reads
                // it from, and is the only lever a snapshot test has for this.
                .environment(\._accessibilityReduceMotion, true),
            named: "day-ribbon",
            // The shared helper's `disablesAnimations: true` default would settle
            // any transaction instantly regardless of what the view does with
            // reduce motion, making this test pass even if `DayRibbonView` ignored
            // the setting entirely. Opting out here is what makes this assertion
            // about the view's own behaviour rather than about the harness.
            disablesAnimations: false
        )
    }

    func testAppRowsFoldTheSubMinuteGlances() {
        // Three ordinary apps, two glances under a minute (folded into one
        // disclosure row), and one that rounds to 0 s — the single case that
        // must not appear anywhere in the image, folded or not.
        let apps: [UsageSeries] = [
            UsageSeries(package: "com.games.puzzle", label: "Puzzle", points: [
                UsagePoint(start: "2026-08-24", foregroundMs: 3_600_000, launchCount: 4),
            ]),
            UsageSeries(package: "com.chat.messenger", label: "Messenger", points: [
                UsagePoint(start: "2026-08-24", foregroundMs: 1_800_000, launchCount: 9),
            ]),
            UsageSeries(package: "com.video.stream", label: "StreamTV", points: [
                UsagePoint(start: "2026-08-24", foregroundMs: 600_000, launchCount: 2),
            ]),
            UsageSeries(package: "com.utility.check", label: "QuickCheck", points: [
                UsagePoint(start: "2026-08-24", foregroundMs: 45_000, launchCount: 1),
            ]),
            UsageSeries(package: "com.weather", label: "Weather", points: [
                UsagePoint(start: "2026-08-24", foregroundMs: 20_000, launchCount: 1),
            ]),
            UsageSeries(package: "com.system.blink", label: "Blink", points: [
                UsagePoint(start: "2026-08-24", foregroundMs: 300, launchCount: 1),
            ]),
        ]
        assert(
            List { Section(header: L("child.apps")) { AppRowsView(series: apps) } },
            named: "app-rows"
        )
    }

    func testDayStrip() {
        // Fixed figures, a fortnight with one zero day: a strip that changes
        // shape between runs is not a snapshot test, it is a random image
        // generator.
        let minutes = [40, 55, 0, 12, 90, 65, 30, 45, 20, 100, 15, 60, 35, 50]
        let days = minutes.enumerated().map { offset, value in
            (day: String(format: "2026-08-%02d", 11 + offset), ms: value * 60_000)
        }
        assert(
            DayStripView(days: days, selected: days[10].day, onSelect: { _ in }).padding(),
            named: "day-strip"
        )
    }

    /// The strip's `Section` sits outside `if let usage` in `ChildDetailView`, so
    /// a day switch — which clears `usage` while the new day's request is in
    /// flight — must never touch it: the parent's finger is still on the bar
    /// they just tapped, and only the sections below (hero, ribbon, apps,
    /// devices) fall back to a skeleton. `usage` and `strip` are non-private
    /// `@State` precisely so this in-between state can be constructed directly
    /// here, since `ChildDetailView` has no `@Observable` model a stub transport
    /// could drive asynchronously (see `ChildDetailViewTests`).
    func testChildDetailDaySwitching() {
        // Same fixed fortnight shape as `testDayStrip`, but keyed to the real
        // last-fourteen-days window `ChildDetailView` computes from `Date()` —
        // fixed calendar dates would drift out of that window and silently
        // render an all-zero strip the day after this test was written.
        let minutes = [40, 55, 0, 12, 90, 65, 30, 45, 20, 100, 15, 60, 35, 50]
        let start = Calendar.current.date(byAdding: .day, value: -13, to: Date()) ?? Date()
        let points = minutes.enumerated().map { offset, value -> UsagePoint in
            let day = Calendar.current.date(byAdding: .day, value: offset, to: start) ?? start
            return UsagePoint(
                start: ISO8601DateFormatter.dayOnly.string(from: day),
                foregroundMs: value * 60_000,
                launchCount: value > 0 ? 1 : 0
            )
        }
        let strip = UsageResponse(
            childId: "kid", from: "irrelevant", to: "irrelevant", bucket: "day", tz: "Europe/Zurich",
            devices: [], series: [UsageSeries(package: "com.games.puzzle", label: "Puzzle", points: points)],
            deviceTotals: []
        )
        let view = ChildDetailView(
            child: ChildResponse(id: "kid", displayName: "Mira", todayMs: 0),
            client: ApiClient(),
            strip: strip
        )
        assert(view, named: "child-detail-day-switching")
    }

    // MARK: - The four languages, where the text is longest

    func testHelpInEveryLanguage() {
        for locale in ["de", "fr", "it", "en"] {
            assert(AgentHelpView(), named: "child-help", locale: locale)
        }
    }
}
