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

**Parent mode manages the family, not just reads it.** The children list adds a
child and removes one; a child's screen disconnects one of their phones, mints
the code that connects the next one, and deletes the figures collected so far.
All five go through `static func`s (`ChildrenView.create`/`.remove`,
`ChildDetailView.revokeDevice`, `PairDeviceView.mintCode`,
`PurgeDataView.purgeData`), for the same reason `fetchUsage` is one: these views
own no `@Observable` model, and `@State` on a view SwiftUI has not installed
silently loses writes, so the testable part stays out of the view lifecycle. Both
destructive actions are a swipe with `allowsFullSwipe: false` followed by a named
confirmation — the swipe opens the question, never answers it.

`PairDeviceView` sits at the foot of the child's screen, **outside** the usage
load: a family whose phone has never reported is exactly the family that needs to
enrol one, and hiding the control behind a failed fetch would lock them out of
the fix. It mints on press, never on appearance — a code lives fifteen minutes
and is claimable once — and shows the server address from the deep link beside
it, because a phone enrolled against the wrong host enrols once and then goes
quiet for good. Above both sits the square: `QrMatrixView` draws the matrix the
server sent — nothing here encodes anything, the one encoder is
`crates/core::qr` — dark on light in both themes, because an inverted QR is
refused outright by some scanners. A server that sent no matrix costs a scan and
nothing else: the code and the address below it pair the phone exactly as they
did before.

`PurgeDataView` sits below it, outside the usage load for the same reason: a day
that failed to fetch is one of the moments a parent is most likely to want the
figures gone. It is the only write here that answers with a body, and the counts
are the point — they are the server's own `rows_affected`, shown even when they
are all zero, because "deleted" with nothing behind it is the one claim a family
has no way to check. `ApiClient` keeps that delete separate from the 204 one so
neither caller can land in the other's case, and a proxy page with a 200 on it
throws rather than being read as a completed purge. Afterwards the fortnight and
the day are blanked and re-read: everywhere else this app keeps loaded numbers
through a refresh, but those bars describe rows that no longer exist.

Removing a child also revokes that child's devices, on the server, in one
transaction. Without it the phone keeps uploading: the device token is
authorized against `devices.revoked_at` alone, so a child hidden from every
parent screen would still have a reporting phone.

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

**The child can see their own fourteen days.** `AgentMyTimeView`, reached from a
`NavigationLink` on `AgentStatusView`, reads the same `GET /v1/me/usage` a
device token buys — this phone's own child, no id in the path. `AgentModel`
drives it with `loadMyTimeStrip()` (the fourteen-day strip, fetched once when
the screen appears) and `selectMyDay(_:)` (one day's hourly detail, the only
request a tap on the strip costs — the strip itself is never refetched). Both
parse the response through the **Rust core** (`parseDayStrip`/`parseDayDetail`),
same as `ingestBody` on the sync side, so a captcha or proxy page throws instead
of reading as an empty day. A `myTimeBusy` guard drops an overlapping call, and
a failed load keeps whatever numbers are already on screen — only the error
line and a retry button appear alongside them.

**The child's side links Beratung 147.** `AgentHelpView`'s last section carries
the same wording the Android agent has always had, in all four languages, with
the link out to 147.ch — the one number on a child's phone that does not report
back to the parents. It is the last section of a `List`, so it sits below the
fold: `screens.child-help-*` show nothing of it, and
`AgentLocalizationTests.testEveryLanguageOffersHelpOutsideTheFamily` is what
holds the copy in place instead.

**What Apple still has to grant.** Family Controls is an entitlement Apple
grants per bundle id. A paid Apple Developer Program membership signs the
**Development** variant — and App Groups — so on our own devices the child role
measures for real. **Family Controls (Distribution)** is a separate, reviewed
request per bundle id (`ch.jorisda.schirmziit`, `.monitor`, `.report`) and is
still outstanding, so TestFlight and the App Store are closed until it lands:
today an iPhone can only be measured by someone who builds and installs the app
themselves. Xcode says so on every build.

Where the entitlement is missing anyway — a fork on a free team, a build with
the capability stripped — the app still runs: role choice, sign-in, enrolment,
syncing all work, but `AuthorizationCenter` refuses, so there are no figures to
report and the status screen says exactly that (`agent.status.unavailable.*`).
The App Group then falls back to the app's own container, which the status
screen also states (`agent.status.shared.warning`). Nothing here pretends to
work.

`docs/platform-matrix.md` is the table of what runs where, iPhone against
Android against the dashboard; it and `site/src/content/matrix.ts` change
together.

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

**`bin/ios-check` is the gate**, and it re-enters the dev shell itself, so none of the
`PATH` care below applies to it. It also checks that the simulator the recipe names is
actually installed before spending three minutes on the Rust core — override the model
with `SCHIRMZIIT_IOS_SIM`, but not for a snapshot run: the goldens are device-specific.

    bin/ios-check                      # the whole gate, from a bare terminal
    bin/check ios                      # the same gate, inside the full set

The rest of this section is for driving the pieces by hand.

**`$HOME/.cargo/bin` must precede `/opt/homebrew/bin` in `PATH`.** Homebrew's rust
shadows rustup and carries no iOS std, so `just ios-core` dies with

    error[E0463]: can't find crate for `core`
      = note: the `aarch64-apple-ios` target may not be installed

while `rustup target list --installed` happily lists that target. `rustup run stable
cargo …` does not help: cargo shells out to a bare `rustc`, which `PATH` resolves to
Homebrew's again. Same trap as the Android note, different std.

    export PATH="$HOME/.cargo/bin:$PATH"

**Check the exit status, do not read the tail.** `just ios-core` failing leaves stale
`ios/Generated/schirmziit_core.swift` behind, and the *next* build then fails with
`cannot find type 'DayTotalFfi' in scope` — a symptom two steps from its cause. Piping
`xcodebuild` into `tail` is worse: the pipeline reports `tail`'s status, so a
`** BUILD FAILED **` run looks like a clean exit 0 and the install fails later with
`not a valid bundle … Info.plist: missing`. Redirect to a file and check `$?`.

    just ios-core                      # Rust core → SchirmziitCoreFFI.xcframework
    just ios-project                   # ios-core, then xcodegen generate
    just ios-check                     # both schemes' tests on a simulator

**Which of the two apps `bin/ios-install` installs:** the real one. It builds the
`Schirmziit` scheme, so the bundle id is `ch.jorisda.schirmziit` and the home screen
says *Schirmziit*. `SchirmziitLocal` is the free-team build, `ch.jorisda.schirmziit.local`,
and its home-screen name is *SchirmziitLocal* — the two are only told apart by that
name, so a phone carrying both from different eras can otherwise show the wrong one.
Delete whichever you are not testing.

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
    # Scheme SchirmziitLocal on a free Personal Team — the `Schirmziit` scheme
    # claims Family Controls and App Groups, which such a team cannot sign.
    xcodebuild -project Schirmziit.xcodeproj -scheme SchirmziitLocal \
      -destination 'id=<device-udid>' \
      -allowProvisioningUpdates DEVELOPMENT_TEAM=S3JX3CJ9SS build

    xcrun devicectl list devices                       # find the device id
    xcrun devicectl device install app --device <id> \
      ~/Library/Developer/Xcode/DerivedData/Schirmziit-*/Build/Products/Debug-iphoneos/SchirmziitLocal.app
    xcrun devicectl device process launch --device <id> ch.jorisda.schirmziit.local

Swap both names for `Schirmziit.app` / `ch.jorisda.schirmziit` once the entitlement
exists and the `Schirmziit` scheme is the one being built.

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
