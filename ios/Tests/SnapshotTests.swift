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
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        for style in [UIUserInterfaceStyle.light, .dark] {
            let traits = UITraitCollection(userInterfaceStyle: style)
            let wrapped = view
                .environment(\.locale, Locale(identifier: locale))
                .frame(width: 393, height: 852)
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
            baseURL: URL(string: "https://schirmziit.jorisda.ch")!,
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
        {"from":"2026-08-11","to":"2026-08-24","series":[
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
        {"from":"2026-08-24","to":"2026-08-24","series":[
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
        await model.selectMyDay(model.mySelectedDay)
        assert(AgentMyTimeView(model: model), named: "my-time")
    }

    // MARK: - Parent mode

    func testSignIn() {
        assert(SignInView(client: ApiClient(), onSignedIn: { _ in }), named: "sign-in")
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

    // MARK: - The four languages, where the text is longest

    func testHelpInEveryLanguage() {
        for locale in ["de", "fr", "it", "en"] {
            assert(AgentHelpView(), named: "child-help", locale: locale)
        }
    }
}
