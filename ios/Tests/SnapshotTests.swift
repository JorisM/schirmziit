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
        role: AppRole? = .child
    ) -> AgentModel {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return AgentModel(
            store: FileHourStore(directory: directory),
            inbox: SnapshotInbox(directory: directory),
            credentials: InMemoryCredentialStore(credentials),
            transport: StubTransport(status: 200, body: "{}"),
            authorizer: StubAuthorizer(state: authorization),
            monitoring: SpyMonitoring(),
            roles: InMemoryRoleStore(role)
        )
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

    // MARK: - The four languages, where the text is longest

    func testHelpInEveryLanguage() {
        for locale in ["de", "fr", "it", "en"] {
            assert(AgentHelpView(), named: "child-help", locale: locale)
        }
    }
}
