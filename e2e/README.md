# End-to-end flows

Two layers of visual/behavioural testing live in this repo, and they answer
different questions.

## 1. Screenshots — no device, part of `just check`-style gates

| Platform | Tool | Where the goldens live | Re-record |
|---|---|---|---|
| Android | Roborazzi (library only, its Gradle plugin still wants an AGP 8 API) | `android/app/src/test/snapshots/` | `./gradlew test -Precord.snapshots` |
| iOS | swift-snapshot-testing | `ios/Tests/__Snapshots__/SnapshotTests/` | `RECORD_SNAPSHOTS=1 xcodebuild … test` |

Both render real UI off-device and compare against committed PNGs, in light and
dark, in German and English (iOS also French and Italian for the help screen —
the longest text, where layouts break first). A changed screen fails the run;
re-recording is a deliberate act, never a way to make red go green.

## 2. Maestro flows — the whole journey, against a real server

Maestro drives the installed app on an emulator or simulator with one YAML
syntax for both platforms. These are the tests that catch wiring: a role choice
that goes nowhere, a claim that never posts, a screen that stays on "signing
in…".

    brew install maestro                       # once
    nu ../home-network/bin/seed-schirmziit.nu  # a throwaway parent + child
    maestro test e2e/flows/child-setup.yaml

Point them at a **throwaway instance**, not the family's. `SCHIRMZIIT_SERVER`,
`SCHIRMZIIT_EMAIL` and `SCHIRMZIIT_PASSWORD` are read from the environment so no
credentials live in the repo.

### Known limits

* **iOS child mode cannot be driven on a simulator.** Family Controls does not
  exist there, so the flow can reach the child-mode screen but only ever sees
  the "not available on this build" state. Real child-mode UI needs a device
  plus Apple's entitlement.
* Android has no such limit: an emulator can grant usage access.

## Recording, and why there is no `--record` flag

`xcodebuild` does not pass plain command-line variables into the test process, so
a `RECORD_SNAPSHOTS=1` flag silently does nothing — images appeared to re-record
only because they were absent. Both sides therefore record by *absence*:

    just ios-record                       # deletes ios/Tests/__Snapshots__, rewrites, verifies
    cd android && ./gradlew test -Precord.snapshots

Both gates are proven to bite: changing a title's font and colour in
`RoleChoiceView` fails `testRoleChoice`, and dropping a heading in the Android
pairing screen failed 5 of the 7 Android images.
