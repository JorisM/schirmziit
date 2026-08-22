# Schirmziit on iOS

Two apps in one Xcode project:

| Scheme | Target | Bundle id | What it is |
|---|---|---|---|
| `Schirmziit` | `Schirmziit` | `ch.jorisda.schirmziit.viewer` | Parent viewer — reads the family's server, measures nothing |
| `SchirmziitAgent` | `SchirmziitAgent` + 2 extensions | `ch.jorisda.schirmziit.agent` | Child agent — measures this phone and reports it |

## The child agent

Structure, mirroring the Android agent as closely as iOS allows:

    AgentShared/     PendingHour, HourStore, UsageSnapshot, SnapshotInbox, HourMarker
                     — the only code the app and its extensions both compile
    AgentSources/    the app: pairing, sync, keychain, Screen Time authorization, UI
    AgentMonitor/    DeviceActivityMonitor extension — wakes on the hour, drops a marker
    AgentReport/     DeviceActivityReport extension — the only place per-app durations exist
    AgentTests/      48 tests, no device and no entitlement needed

Everything testable lives in the `SchirmziitAgentKit` framework, because a test
bundle hosted by the app can never launch on a simulator: the Family Controls
extensions cannot be installed there (`extensionDictionary must be set in
placeholder attributes`). Tests link the framework instead of the app.

**Why the pipeline looks different from Android's.** iOS has no per-app
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
as on Android, so the two agents cannot drift. `ContractTests` additionally
checks that body against `api/openapi.json`.

**What does not work without Apple's approval.** Family Controls is an
entitlement Apple grants per app; a free Personal Team cannot sign it, and App
Groups need a paid team too. Without both, the app builds, installs and runs —
it pairs, it queues, it syncs — but `AuthorizationCenter` refuses, so there are
no figures to report and the status screen says exactly that
(`status.unavailable.*`). The App Group falls back to the app's own container,
which the status screen also states (`status.shared.warning`). Nothing here
pretends to work.

## The parent viewer

Reads a family's own Schirmziit server and shows a child's screen time. It
measures nothing on the phone it runs on.

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
    xcodebuild -project Schirmziit.xcodeproj -scheme SchirmziitAgent \
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
    xcrun devicectl device process launch --device <id> ch.jorisda.schirmziit.viewer

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
    xcrun simctl launch <simulator-id> ch.jorisda.schirmziit.viewer

## Design

Apple's HIG for structure — grouped lists, navigation stack, SF Symbols, SF Pro,
pull-to-refresh — wearing the dashboard's palette, so the phone and the browser
read as one product. Dynamic colour stays off deliberately. Four languages
(de/fr/it/en) following the system language; `LocalizationTests` fails the build
if one falls behind.
