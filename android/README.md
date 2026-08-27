# schirmziit android

## One app, two roles

The app asks what the phone is before it asks for a password — the same first
question iOS asks, and for the same reason: what a phone is decides everything
else it does.

| Choice | What happens |
|---|---|
| *My phone* | The parent signs in and reads the numbers. Nothing is measured here, and no usage-access permission is asked for. |
| *My child's phone* | The parent signs in once, picks the child, and the app trades that session for a device token — then ends the session. The phone reports from then on. |

`role/AppRole.kt` holds the enum, the gate (`destination`), and the two functions
that matter more than either: **`adoptRole` destroys the other role's
credential**, and `forgetRole` destroys both. That is the on-device half of the
rule `crates/server/tests/tenancy.rs` proves on the server — a device token is a
write credential for one child's data and a parent session reads the whole
family, so one phone must never hold both. It is reachable by an ordinary route,
too: a phone that was a child's and gets handed on becomes a parent phone, and
nobody reinstalls the app first. `RoleGateTest` is what holds it.

**A phone that is already enrolled is never asked.** `resolveRole` answers from
the device token when no role has been stored yet, because every phone installed
before the role question existed is in exactly that state — and asking would put
a child that is reporting fine one tap away from *My phone*, where `adoptRole`
would do its job and destroy the token. The inferred answer is written down, so
the phone behaves the same after it is later unpaired.

The two credentials live in **separate encrypted files** — `AgentStore`
(`schirmziit-agent`) for the device token, `EncryptedParentSession`
(`schirmziit-parent`) for the session cookie — rather than as four fields on one
store. Two stores cannot be mixed up by a careless `edit()`.

### The parent screens

`ui/parent/`, driven by `ParentApp`. Everything decidable is a plain function in
`parent/` with tests (`ParentUiStateTest`, `PairingStateTest`); the composables
only render it. Same split as `MyTimeRepository`/`mergeMyTimeResult` on the child
side, and for the same reason — the rules about never losing a day are worth
asserting without a server or an emulator.

**The numbers come from `crates/core`.** `ChildDetailScreen` renders
`parseDayStrip`/`parseDayDetail` — the same two functions the child's own
`MyTimeScreen` calls. iOS decodes the parent side by hand into `UsageResponse`;
this does not, so a parent and a child looking at the same day cannot be shown
different totals, and a captcha page throws instead of reading as an empty day.
Only the `devices` array, which the core ignores, is parsed in Kotlin
(`ParentClient.devices`).

**Errors come from the catalog.** `ui/parent/ErrorPanel` reads the generated
`error_copy.xml` (`copy/errors.toml` → `just gen-copy`), which had existed in
four languages on Android for a while with nothing reading it. Every failure
carries `SZ-Ennn` and a six-character reference, so a photographed screen
identifies itself. The child agent's own screens still hand-write their
sentences and owe the same treatment.

**Motion.** The parent screens meet the full bar — entry motion, press feedback,
one flourish each (the count-up on the children list, the ribbon fill on a
child's day), skeletons shaped like the content. `ui/parent/Motion.kt` holds the
tokens and `rememberReducedMotion()` reads
`Settings.Global.ANIMATOR_DURATION_SCALE`. **The child agent stays motion-free** —
it is a background collector and battery is its budget, which is why these live
under `ui.parent` rather than next to the theme.

`ParentScreenshotTest` records every parent screen with animations switched off
at the system level. That is deliberate twice over: it makes the goldens
deterministic, and it captures the *reduced-motion* path, so a bar at 20 % height
in one of those images is a bug rather than a timing artefact.

`PurgeDataCard` deletes a child's stored figures — the same act as the
dashboard's `PurgeData`, and the only irreversible write here that answers with
numbers. It asks twice, the standing control is quiet and the confirm inside the
question is the red one, and afterwards it shows the server's own
`rows_affected`: "deleted" with nothing behind it is exactly the claim a family
cannot check. Zeros are shown as zeros, so a purge that matched nothing is
legible as one. `purgedDay` then blanks the fortnight and the day and re-reads
both — the one place this app deliberately drops loaded numbers, because those
bars describe rows the server has just deleted.

### What the parent mode does not do yet

The pairing code is shown as **text**, not as a QR: Android is the one place that
could render one, since zxing is already a dependency here for the child app's
scanner. `docs/platform-matrix.md` tracks it.

## Two ways to connect a phone

**Signing in with the parent account is the default** (`ParentSetup`): the parent
signs in once on the child's phone, picks the child, and the app claims a device
token through `POST /v1/children/{id}/devices` — then ends the session. Order
matters and is tested: the token is stored before the logout, and a failed claim
stores nothing but still logs out. A child's phone must never be left holding a
parent session.

**Pairing codes still work** (QR or six characters typed), for the case where
the parent is not standing there. That is the only reason the code path exists.

A typed code is six characters (`ENROLL_CODE_LENGTH`, mirroring the server's
`ENROLL_LEN`). It is a named constant and a function because the Connect button
used to compare against `8` inline, after the server had moved to six — so the
button could never be pressed on any phone, and no test could see it. That is
`EnrollCodeTest` now.

## My time

`MyTimeScreen`, reached from `StatusScreen` (`onOpenMyTime`), shows the child
its own fourteen-day strip and, on a tap, one day's hourly detail — the same
`GET /v1/me/usage` a device token buys, no path id, own child only.
`MyTimeRepository.load(selected, from, tz)` fetches both requests and parses
each through the **Rust core** (`DayTotalFfi`/`DayDetailFfi`, same
`parseDayStrip`/`parseDayDetail` the agents share), so a captcha or proxy page
throws instead of reading as an empty day. A failed load reports `failed = true`
with no numbers — but that empty `MyTime` never reaches the screen as-is:
`mergeMyTimeResult` (`mytime/MyTimeUiState.kt`) keeps whatever `MyTime` was
already on screen and raises `myTimeError` separately, so a dropped connection
adds an error line and a retry button above the previous numbers instead of
wiping them. `MainActivity` keeps a still-loading first open (`myTimeLoading`)
apart from `MyTime` itself, because an earlier version faked an empty `MyTime`
while loading and `MyTimeScreen` rendered that as "nothing recorded" — the same
silent-zero lie the failed state exists to prevent, arriving through latency
instead of a dropped connection. `pendingDay` is compared against the day that
lands so a stale response from an earlier tap is dropped rather than
overwriting a newer selection, and retry re-issues the load for whichever day
is still `pendingDay`.

## Background listening

Media playing **while the screen is off**, per app, per hour. A second measure
next to screen time, never folded into it: `background_ms` on each app row,
`background_measured` on each hour.

**Why MediaSession, behind a grant.** `MediaSessionManager.getActiveSessions()`
is the only public API that says *which app* is playing, and it requires the
notification-listener grant the user makes in Settings. The alternatives were
tried on paper and rejected: `UsageEvents.FOREGROUND_SERVICE_START/STOP` needs
no new grant but carries no service *type*, so a 40-minute podcast and a
40-minute backup are indistinguishable; `AudioManager.getActivePlaybackConfigurations()`
keeps `getClientUid()` as `@SystemApi`, so a normal app learns that audio is
playing and never whose.

`PlaybackListener` is a `NotificationListenerService` because the *system* binds
and restarts it — no foreground service of ours, no battery budget spent keeping
a process alive. On `onListenerConnected` it snapshots what is already playing,
or an audiobook that started before a rebind would never be counted.

**Notifications are never read.** `onNotificationPosted` and
`onNotificationRemoved` are empty overrides, and `PlaybackReader`'s type surface
is `(package, playing)` — there is no field a track title could travel in.
`PlaybackReaderTest` asserts that field list, and `scripts/check-no-content.sh`
(wired into `just android-check`) fails the build if any main source reaches for
`MediaMetadata`, notification content, or lets either callback stop being empty.

**Declining is a supported end state**, not a broken setup. One dismissible card
on the status screen explains the grant; say no and the phone reports
`background_measured = false` forever, which every surface renders as "not
counted here" rather than as a zero. iPhones report the same false: Screen Time
counts foreground only and no third-party API exposes another app's playback.

**Screen state.** `EventMapper` maps `SCREEN_INTERACTIVE` to `ScreenOn` purely
for this: `KEYGUARD_HIDDEN` does not fire when the screen wakes already
unlocked, so without it a stretch would never close. It is inert for foreground
sessions — a `RESUMED` always follows — and there is a core test pinning that.

The stretch itself is stitched in `crates/core`, not in Kotlin, and its state at
a window boundary is carried in `playback_carry` (Room v2) exactly as the open
foreground app is carried in `carry_over`.

## Toolchain (pinned, as installed 2026-08-21)

| Tool | Version | Install |
|---|---|---|
| JDK | 21.0.12 (Homebrew openjdk@21) | `brew install openjdk@21` |
| Android SDK | platforms 36 + 37.1, build-tools 36.1.0 + 37.0.0, platform-tools 37.0.1 | `brew install --cask android-commandlinetools` |
| NDK | 29.0.14206865 (newest stable; r30 is still rc) | `sdkmanager "ndk;29.0.14206865"` |
| Rust targets | aarch64-linux-android, x86_64-linux-android | `rustup target add …` |
| cargo-ndk | 4.x | `cargo install cargo-ndk` |
| Gradle | 9.7.1 (wrapper committed) | `brew install gradle` once, then `gradle wrapper` |
| AGP | 9.3.1 | — |
| Kotlin | 2.4.10 (via AGP's built-in Kotlin) | — |
| KSP | 2.3.11 | — |
| UniFFI | 0.32 | — |

## Version constraints learned the hard way

- **AGP 8.x does not work with Gradle >= 9.6** — it relies on `InternalProblems`,
  removed there. AGP 9.3.1 + Gradle 9.7.1 is the combination that builds.
- **AGP 9 has Kotlin support built in.** Applying `org.jetbrains.kotlin.android`
  is a hard error; the plugin list must not include it.
- **compileSdk must be 37**, not 36: Compose BOM 2026.08.00 ships artifacts that
  require API 37 and `checkDebugAarMetadata` fails otherwise.
- JVM unit tests need a **host** build of the core (`libschirmziit_core.dylib` in
  `app/src/test/resources/darwin-aarch64/`), which `just android-bindings`
  produces. Without it every test fails with `UnsatisfiedLinkError`.

Environment (add to your shell rc):

    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
    export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
    export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/29.0.14206865
    export PATH="$HOME/.cargo/bin:$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

**`$HOME/.cargo/bin` must come before `/opt/homebrew/bin`.** Homebrew's `rust`
formula is also installed on this Mac and shadows rustup; its rustc has no
Android std, so `cargo ndk` fails with `can't find crate for 'core'` even though
`rustup target list` shows the target installed.

## Why this project lives at the repo root

It was moved out of `pve/` on 2026-08-21. `sqlx`'s query macros walk **every**
ancestor directory looking for `.env` and hard-fail on one they cannot parse
(`sqlx-macros-core/src/query/metadata.rs`). `pve/.env` holds two Google-style app
passwords whose values contain spaces and are unquoted, which dotenvy rejects —
so no crate under `pve/` can compile with the macros. Moving up one level also
matches where this project is headed: its own repository.

`schirmziit/.env` (gitignored) carries the local `DATABASE_URL` and
`SQLX_OFFLINE=true`.

## Build

`bin/android-check` is the gate, and it re-enters the dev shell itself — so none of
the `PATH`, `JAVA_HOME`, `ANDROID_HOME` and `ANDROID_NDK_HOME` care above applies to
it. The environment block is for driving gradle by hand.

    bin/android-check      # the whole gate, from a bare terminal
    bin/check android      # the same gate, inside the full set

    just android-check     # rust core for android + gradle unit tests
    cd android && ./gradlew assembleDebug
    adb install -r app/build/outputs/apk/debug/app-debug.apk

## Screenshots

`app/src/test/snapshots/` holds one PNG per screen and state, rendered by
Roborazzi through Robolectric — no emulator. `./gradlew test` compares against
them; `./gradlew test -Precord.snapshots` re-records.

Roborazzi is used as a plain library: its Gradle plugin (1.53) still asks AGP for
`TestedExtension`, which AGP 9 removed. The system properties it reads are set in
`app/build.gradle.kts` instead.

Two Android-specific gotchas, both found the hard way:

* Robolectric qualifiers must be in Android's canonical order, and a class-level
  `@Config` is *concatenated* in front of the method's — which breaks that order.
  Hence full qualifier strings per test, with `night` before the density.
* `createComposeRule()` wants a host activity a library-less unit test does not
  have. Roborazzi's `captureRoboImage("…") { composable }` captures a composable
  directly and needs none.
