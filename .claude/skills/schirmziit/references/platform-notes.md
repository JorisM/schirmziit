# Platform notes

Traps that cost real time here. Each one was hit; none is theoretical.

## Rust / server

* **`.env` in an ancestor directory breaks every sqlx macro.** sqlx walks *all* parent
  directories for `.env` and hard-fails on a malformed one. This is why the project sits at
  `~/Projects/schirmziit` and not inside `home-network`, whose `pve/.env` has unquoted
  values with spaces.
* `openapi-check` and `gen-check` diff **against git**, so they fail on regenerated-but-
  uncommitted files. That is by design; commit the regenerated file.
* Postgres 18 in CNPG, and the container refuses a volume mounted at
  `/var/lib/postgresql/data` — mount `/var/lib/postgresql` (see `deploy/docker-compose.yml`).
* **A CNPG app role keeps its bootstrap password.** Re-rendering the secret does not change
  it, and the app crashloops with `28P01` while pass, the secret and the URL all agree. Fix
  with `ALTER ROLE … WITH PASSWORD` from the primary, then delete the pod.

## Android

* AGP 9 removed `TestedExtension`, so **Roborazzi's Gradle plugin cannot be applied**. The
  library is used directly with its system properties set in `app/build.gradle.kts`.
* Robolectric qualifiers must follow Android's canonical order, and a class-level `@Config`
  is *concatenated in front of* the method's — which breaks that order. Use one full
  qualifier string per test, with `night` before the density.
* `createComposeRule()` needs a host activity a library-less unit test does not have.
  `captureRoboImage("…") { composable }` captures a composable directly.
* A straight apostrophe in a `strings.xml` value fails the resource merger with "Invalid
  unicode escape sequence". Use the typographic `’`, which the rest of the files use.
* JVM unit tests reach the Rust core through JNA and need a **host** build in
  `app/src/test/resources/darwin-aarch64/` — `just android-bindings` puts it there.
* `$HOME/.cargo/bin` must precede `/opt/homebrew/bin`: Homebrew's rust shadows rustup and
  has no Android std, which surfaces as "can't find crate for `core`".
* Room on the main thread crashes the activity; read through `produceState` + `Dispatchers.IO`.
* App labels come back as package ids without `QUERY_ALL_PACKAGES`.

## iOS

* **Everything testable lives in `SchirmziitKit`.** The Family Controls extensions cannot be
  installed on a simulator (`extensionDictionary must be set in placeholder attributes`), so
  a test bundle hosted by the app can never launch. Tests link the framework.
* The framework carries its own `.lproj` folders and resolves strings through
  `Bundle.schirmziitKit`; `L("key")` exists because SwiftUI's `Text("key")` reads
  `Bundle.main`, which is the **test runner** under test — a main-bundle lookup renders raw
  keys in every snapshot. `String(localized:)` follows the *process* locale, so field
  placeholders appear in English in a German snapshot while being correct on a phone.
* `.lproj` folders must be listed with `type: folder` in `project.yml`, or the four locales
  are flattened into one variant group and a parity test passes while reading one language.
* **No `UILaunchScreen` key means a letterboxed app**, not a missing splash screen: iOS
  falls back to legacy compatibility mode, renders at an older screen size and puts a black
  bar above and below on every modern iPhone. Nothing in the SwiftUI code says so and no
  snapshot can catch it — the bars are the window, not the layout. The empty dictionary in
  `project.yml` is the whole opt-in, and it is what then makes the iPad device family demand
  all four orientations.
* **Both app targets generate the same `Sources/Resources/Info.plist`**, so its `properties`
  are a YAML anchor shared between them. Two copies drift, and the target generated last
  wins silently.
* **Snapshots record by deletion** (`just ios-record`). `xcodebuild` does not pass plain
  command-line variables into the test process, so a record flag silently does nothing and
  images only change when absent — which looks like a stable suite comparing stale images.
* `NavigationStack` lays its bar out asynchronously: snapshots need
  `.wait(for: 0.5, on: .image(precision: 0.99, perceptualPrecision: 0.98, …))` or they
  differ run to run.
* `$HOME/.cargo/bin` must precede `/opt/homebrew/bin` here too — Homebrew's rust has no iOS
  std either, and `rustup run stable cargo` does **not** save you: cargo shells out to a bare
  `rustc`, which `PATH` resolves back to Homebrew's.
* **A failed `just ios-core` is invisible two steps later.** It leaves the previous
  `ios/Generated/schirmziit_core.swift` in place, so the next build fails with
  `cannot find type 'DayTotalFfi' in scope` — which reads like drifted bindings rather than a
  Rust build that never ran. Check the exit status, not the log tail.
* **Never pipe `xcodebuild` into `tail`/`grep`.** The pipeline reports the filter's status, so
  `** BUILD FAILED **` exits 0 and looks clean; the lie surfaces at install time as
  `not a valid bundle … Info.plist: missing` from an `.app` holding only `Frameworks/`.
  Redirect to a file and test `$?`. (The `curl | jq` rule from home-network, on a Mac.)
* On a free Personal Team the installable target is **`SchirmziitLocal`**
  (`ch.jorisda.schirmziit.local`), not `Schirmziit` — the latter claims Family Controls and
  App Groups. It has no `PRODUCT_NAME` override but still builds `SchirmziitLocal.app`.
* Xcode **beta** at `~/Downloads/Xcode-beta.app` (not `/Applications`); point `DEVELOPER_DIR`
  at it per command. `DEVELOPMENT_TEAM` is the certificate's **OU**, not the id in
  parentheses. A free Personal Team means a 7-day profile.
* Family Controls and App Groups both need a paid team. Without them the app runs and says
  so on screen (`agent.status.unavailable.*`, `agent.status.shared.warning`) — keep that
  honesty when touching those states.
* iOS has **no per-app event stream**: per-app durations exist only inside a
  `DeviceActivityReport` extension, which has no network access. Hence marker →
  invisible report view → snapshot file in the App Group → app uploads. Do not try to
  "simplify" that away.

## Deployment / infra

* Image builds run in local Docker on the Mac; pve has no Docker daemon. Cloudflare WARP
  TLS-inspects and breaks builds and `pass-cli` — `warp-cli disconnect` first, and it
  re-connects itself after ~4 minutes.
* `bin/deploy-k8s.nu` substitutes `:PLACEHOLDER`; `kubectl apply -f` on a self-built
  service's directory fails on purpose.
* The dashboard host has no Let's Encrypt certificate while the port-80 hairpin is broken
  (HTTP-01 gets `Connection refused` from outside); the same failure hits other hosts, so
  it is not a Schirmziit problem.
