# Schirmziit on iOS

**One app, two roles.** `Schirmziit` (`ch.jorisda.schirmziit`) asks what the phone
is before it asks for a password:

| Choice | What happens |
|---|---|
| *My phone* | Parent signs in, sees the dashboard. Nothing is measured here, no Screen Time access asked for. |
| *My child's phone* | Parent signs in **once**, picks the child, and the app trades that session for a device token — then ends the session. The phone reports from then on and shows the child-facing screen. |

Leaving child mode needs the parent password, checked against the server. Child
mode a child can tap out of is decoration, so `AgentModel.leaveChildMode` is the
one place where a wrong answer must change nothing.

Pairing codes still exist for the case where the parent is not there to sign in;
the parent-session path is what removes the typing for everyone else.

## Structure

    Sources/Api        ApiClient, response models (parent side)
    Sources/Design     palette, list style, formatting
    Sources/Views      role choice, child setup, sign-in, dashboard, ribbon
    Sources/Role       AppRole + RoleStore
    AgentShared/       PendingHour, HourStore, UsageSnapshot, SnapshotInbox, HourMarker
    AgentSources/      child-mode logic and UI: sync, keychain, Screen Time, setup
    AgentMonitor/      DeviceActivityMonitor extension — wakes on the hour
    AgentReport/       DeviceActivityReport extension — the only per-app durations
    Tests/, AgentTests/  70 tests, no device and no entitlement needed

Everything except `@main` lives in the **SchirmziitKit** framework. Not a style
choice: the Family Controls extensions cannot be installed on a simulator, so a
test bundle hosted by the app can never launch (`extensionDictionary must be set
in placeholder attributes`). Tests link the framework instead.

The framework also carries its own copy of the four `.lproj` folders and looks
strings up in `Bundle.schirmziitKit`. `Bundle.main` is the app when the app runs
and the *test runner* when tests run, so a main-bundle lookup returns raw keys
under test — which is exactly how it was found.

**Why the child pipeline looks different from Android's.** iOS has no per-app
foreground event stream. The only source of per-app durations is a
`DeviceActivityReport` extension, which is a SwiftUI scene with **no network
access**, and it computes numbers only while a report view is on screen. So:

1. the monitor extension wakes on the hour and writes `last-hour.json`;
2. the app keeps an invisible `DeviceActivityReport` on the status screen
   (`UsageProbeView`), which makes iOS run the report extension;
3. the report extension writes `snapshot-<hour>.json` into the App Group;
4. the app drains those, hands them to the **Rust core** (`planNextSync`,
   `ingestBody`, `applyIngestResult`) and POSTs `/v1/ingest`.

Step 4 is the important one: the wire format is built by `crates/core`, exactly
as on Android, so the two agents cannot drift. `AgentContractTests` additionally
checks that body against `api/openapi.json`.

**What does not work without Apple's approval.** Family Controls is an
entitlement Apple grants per app; a free Personal Team cannot sign it, and App
Groups need a paid team too. Without both, the app builds, installs and runs —
role choice, sign-in, enrolment, syncing — but `AuthorizationCenter` refuses, so
there are no figures to report and the status screen says exactly that
(`agent.status.unavailable.*`). The App Group falls back to the app's own
container, which the status screen also states
(`agent.status.shared.warning`). Nothing here pretends to work.


## Toolchain

| Tool | Version | Where |
|---|---|---|
| Xcode | **27.0** (27A5237l) | `~/Downloads/Xcode-beta.app` — *not* in `/Applications` |
| iOS SDK | 27.0 | in the beta only; `/Applications/Xcode.app` is 26.6 with SDK 26.5 |
| Device | iPhone 16 Pro on iOS 27.0 | must be paired in Xcode → Window → Devices |
| xcodegen | brew | regenerates `Schirmziit.xcodeproj` from `project.yml` |

The phone runs iOS 27, so builds must use the beta. No `sudo xcode-select`
needed — point `DEVELOPER_DIR` at it per command:

    export DEVELOPER_DIR=~/Downloads/Xcode-beta.app/Contents/Developer

## Build, test, install

    just ios-core                      # Rust core → SchirmziitCoreFFI.xcframework
    just ios-project                   # ios-core, then xcodegen generate
    just ios-check                     # both schemes' tests on a simulator

Or by hand:

    cd ios
    xcodegen generate                  # after editing project.yml

    # Unit tests on a simulator (no signing, no entitlement)
    xcodebuild -project Schirmziit.xcodeproj -scheme Schirmziit \
      -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test CODE_SIGNING_ALLOWED=NO

    # Device build. DEVELOPMENT_TEAM is the certificate's OU, which is NOT the
    # value in parentheses in the identity's name:
    #   security find-certificate -a -c "Apple Development" -p \
    #     | openssl x509 -noout -subject
    #   → OU=S3JX3CJ9SS  ← team;  CN=… (6TCRCYDSQQ)  ← not the team
    xcodebuild -project Schirmziit.xcodeproj -scheme Schirmziit \
      -destination 'id=<device-udid>' \
      -allowProvisioningUpdates DEVELOPMENT_TEAM=S3JX3CJ9SS build

    xcrun devicectl list devices                       # find the device id
    xcrun devicectl device install app --device <id> \
      ~/Library/Developer/Xcode/DerivedData/Schirmziit-*/Build/Products/Debug-iphoneos/Schirmziit.app
    xcrun devicectl device process launch --device <id> ch.jorisda.schirmziit

First launch after a fresh install fails with `FBSOpenApplicationErrorDomain
error 3` until the profile is trusted on the phone: Settings → General → VPN &
Device Management → Developer App → Trust.

**Free Personal Team means a 7-day profile.** After a week the app refuses to
launch; rebuild and reinstall with the commands above. A paid account removes
that, and is also the prerequisite for ever building the iOS child agent.

## Screenshot / demo runs

Debug builds read a few environment variables so a run can sign in without
tapping (absent from release builds):

    SIMCTL_CHILD_SCHIRMZIIT_SERVER=https://schirmziit.example.ch \
    SIMCTL_CHILD_SCHIRMZIIT_EMAIL=… SIMCTL_CHILD_SCHIRMZIIT_PASSWORD=… \
    SIMCTL_CHILD_SCHIRMZIIT_AUTOLOGIN=1 SIMCTL_CHILD_SCHIRMZIIT_OPEN_FIRST_CHILD=1 \
    xcrun simctl launch <simulator-id> ch.jorisda.schirmziit

## Design

Apple's HIG for structure — grouped lists, navigation stack, SF Symbols, SF Pro,
pull-to-refresh — wearing the dashboard's palette, so the phone and the browser
read as one product. Dynamic colour stays off deliberately. Four languages
(de/fr/it/en) following the system language; `LocalizationTests` fails the build
if one falls behind.
