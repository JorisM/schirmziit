---
name: schirmziit
description: Use when working on Schirmziit — the family screen-time product in ~/Projects/schirmziit (Rust core + axum server, React dashboard, Android and iOS apps, Astro site). Covers what the product is and is not, the invariants a feature must not break, the test gates, the copy rules for its four languages, and the motion bar its screens meet.
---

# Schirmziit

Free, open-source, self-hostable screen-time monitoring for families. Swiss product,
homelab-hosted today, public beta later. A parent sees **how long and when** a child's
phone is used, so screen time can be talked about instead of guessed.

**Read `docs/` and the per-platform READMEs before changing a subsystem**: `ios/README.md`,
`android/README.md`, `e2e/README.md`. Platform traps that cost real time are in
`references/platform-notes.md` — check it before debugging a build.

## What this product is, and is not

| Is | Is not |
|---|---|
| Hourly per-app foreground time, unlocks/pickups, time of day | Any content: no messages, searches, photos, keystrokes, URLs, location |
| Visible on the child's own phone, same numbers the parent sees | Covert monitoring — never write copy that implies hiding |
| A basis for a conversation | A remote control: nothing is blocked, no app is switched off, no time limits |
| Self-hosted by default, hosted instance optional | A service that keeps a family's data on someone else's account |

Blocking and time limits are a deliberate, separate, later step. If a request implies
either, say so and ask before building it.

**One app, two roles** (decided 2026-08-22, before the beta): the app asks what the phone
is, then only the login that role needs. On a child's phone the parent signs in once,
picks the child, and the app trades that session for a device token — then ends the
session. Leaving child mode re-checks the parent password against the server. Pairing
codes remain for when the parent is not there to sign in.

## Invariants — a feature that breaks one of these is wrong

1. **The wire format lives in `crates/core` only.** Both agents build request bodies with
   `ingestBody`/`planNextSync`/`applyIngestResult` through UniFFI. Never hand-roll the
   ingest JSON in Kotlin or Swift; that is how two agents drift.
2. **Tenancy is proved by the type system.** Parent routes take the `Parent` extractor and
   scope through `db::scope::*`; another family's id must return 404, not 403. Device
   tokens are write-only apart from one deliberate exception — `GET /v1/me/usage`, own
   child only, no id in the path — and a device must never read anything else
   (`/v1/children` with a bearer token is 401 and there is a test for it). Every other
   parent route still returns 401 for a device token, and `crates/server/tests/tenancy.rs`
   proves it. `POST /v1/waitlist` is the one route no family owns at all: a public
   write, no session, nothing readable back, and its own wildcard CORS grant so the
   marketing site never has to join `DASHBOARD_ORIGINS`.
3. **Nothing that can lose a day.** The queue only drops an hour the server accepted or
   permanently rejected; an unparseable response (captcha, proxy page) must throw, never
   read as "all accepted". A recomputed hour never replaces a fuller one — that bug
   shipped once on Android and is now guarded on both platforms.
4. **`background_ms` is never screen time.** Background listening — media playing with
   the screen off — is a separate measure everywhere: its own column, its own field, its
   own colour. No query, component or total adds it to `foreground_ms` or `screen_on_ms`.
   `background_measured = false` means "this device could not observe it", never "nothing
   played"; rendering the two alike is the silent zero the rest of this codebase avoids.
   Android only (MediaSession behind an opt-in notification grant); iOS reports false.
5. **Services are ClusterIP-only behind Traefik.** A MetalLB IP would serve the app to
   VLAN 10 outside CrowdSec, rate limiting and forward auth.
6. **Four languages, always.** de (Schweizer Hochdeutsch: no ß, du-form), fr, it, en.
   Every new string lands in all four in the same commit; parity tests fail otherwise.
7. **Secrets come from pass-cli via `*.yaml.tpl`.** No secret in a manifest, no plaintext
   in the repo.

## The gates

Run the gate for what you touched; run all of them before saying a feature is done.

    just rust-check      # fmt, clippy -D warnings, sqlx prepare --check, openapi-check, cargo test
    just web-check       # generated types match openapi.json, tsc, vitest
    just android-check   # uniffi bindings + gradle test (unit + Roborazzi screenshots)
    just ios-check       # xcodegen + xcodebuild test (unit + snapshot images)
    cd site && pnpm build

**The type-parity chain is the point**: Rust handlers → `api/openapi.json` → `web/src/api/schema.d.ts`.
`openapi-check` and `gen-check` compare against git, so a schema change that is not
regenerated *and committed* fails. Adding a field to a response means: handler, `openapi.rs`
`components(schemas(...))` if it is a new type, `just openapi`, `just gen`, commit.

## How to work

- **A reported bug or change starts at the test suite, not at the code.** Before
  touching anything, look for a test that already covers the behaviour. If one
  exists, say so — the bug is then either outside what it asserts or the test is
  vacuous, and both are useful findings. If none exists, judge whether one can be
  written honestly: some things (a password manager's heuristics, a glass material
  that only composites on device, an entitlement Apple has not granted) cannot be
  asserted in a test, and pretending otherwise produces a test that passes while
  the phone stays broken — say that plainly instead. When it *can* be tested, add
  the test first, watch it fail for the reported reason, and develop against it.
- **TDD, and prove the test is not vacuous.** Write the failing test, run it, watch it fail
  for the right reason, then implement. For anything that could silently lose or expose
  data, mutate the implementation afterwards and confirm the test goes red — several tests
  in this repo were written that way and one earlier test was vacuous until it was checked.
- **Comments say why, not what.** The existing code explains the trap it avoids; match that.
- **Deploy** from `~/Projects/home-network` on `main`: `nu bin/deploy-k8s.nu schirmziit`
  (and `schirmziit-site`, `umami`). The image tag is the home-network HEAD sha, so **commit
  first**. `kubectl` runs locally, never through `hn run`.
- **Verify against the deployed thing**, not just tests: probe from a pod through Traefik,
  or `kubectl port-forward deploy/schirmziit`. The netpol allows only Traefik and Gatus, so
  a plain probe pod cannot reach the service.
- Never run a probe that writes against the live instance without checking what is in the
  database first — a registration probe once took the instance's `first-user-only` slot.

## Copy rules

Plain language, short sentences, no marketing. Say what happens, in the reader's own terms.

- Frame monitoring as protection, never as secrecy. There are tests that fail on
  "heimlich", "sneak", "en cachette", "di nascosto" in any locale — do not work around them.
- The child-facing app speaks to the child directly, in the du-form, and links Beratung 147
  (which does not report back to the parents). Keep that link.
- Parent-facing help links the four Swiss sources (Jugend und Medien, Pro Juventute, 147,
  Zischtig) — the same list as the site, so both agree.
- Errors say what happened and what to do; empty states invite an action.

## Feel — the app is meant to be a pleasure to open

A parent who enjoys opening the dashboard opens it again, and a parent who opens it
thinks about their child's week. Motion is not decoration here, it is what makes the
habit stick.

**Every screen that shows data ships with all four:**

1. **Entry motion on the data itself.** Charts draw in (the web charts are hand-rolled
   divs — CSS keyframes on the `--motion-*` tokens, staggered with `--motion-stagger`;
   recharts is a dependency nothing imports), stat numbers count up from zero, list rows
   stagger ~40 ms apart. No data grid that simply appears.
2. **A reaction to every touch.** Press and hover states, tooltips that track the cursor,
   the hovered bar lifting, the tab underline sliding. Nothing flips state with no
   transition.
3. **One deliberate flourish per screen** — the day ribbon filling hour by hour, today's
   column glowing, an arrow morphing up or down against last week, a counter racing to
   this week's total. One. Two flourishes compete and both lose.
4. **Skeletons shaped like the final content**, never a spinner over the layout; data
   crossfades in over its own skeleton.

**Error states get entry motion and press feedback, but no flourish.** The flourish
belongs to the data. An interface that animates a failure is enjoying itself at the
parent's expense. An error panel takes the footprint its skeleton had, and a *refresh*
that fails leaves the loaded data on screen with a banner over it — blanking a chart
because a poll failed loses a day at the presentation layer.

**Budget:** 200–400 ms typical for entry and transition motion, 600 ms for a hero
count-up. That band describes how long a screen's own transitions run — it is not a
floor. Press feedback (`--motion-fast`, 120 ms) and the per-row stagger offset
(`--motion-stagger`, 40 ms) are a different category, deliberately quicker than any
entry duration, not an exception to this budget. Motion never delays reading —
a number is legible on the first frame even mid-animation, and nothing important waits
for an animation to end. The Astro site meets the same bar with scroll-reveal, and every
word still renders with JS off.

**Gamify the parent's habit of looking, never the child.** Delight belongs to the
interface: motion, colour, a warm empty state, an insight worth coming back for
("evenings are up 40 min this week"). It never scores, ranks, rewards or shames the
person the numbers describe — no confetti for a "good" day, no streak a child can lose,
no badge on a child. The child's own view gets the same craft and the same absence of
judgement.

**Reduced motion is a first-class path, not a fallback:** `prefers-reduced-motion`
(Tailwind `motion-reduce:`), `@Environment(\.accessibilityReduceMotion)` on iOS,
`Settings.Global.ANIMATOR_DURATION_SCALE == 0f` on Android. It lands on the final state
instantly — never a half-drawn chart.

**Tests assert the settled state.** Roborazzi and the iOS snapshots capture after the
animation, so drive the Compose test clock or take the shot post-settle; vitest asserts
final values. Loosening an assertion to accommodate an animation is the wrong fix.

**The Android child agent stays motion-free** — it is a background collector and battery
is its budget.

## Definition of done

- [ ] Tests written first, and the important ones proven to fail when the code is broken
- [ ] All strings in de/fr/it/en; parity tests green
- [ ] Type-parity chain regenerated and committed if the API changed
- [ ] Screenshots re-recorded **deliberately** if a screen changed, and looked at
- [ ] Every gate green, including the platforms you did not touch
- [ ] Deployed and verified against the running instance, or explicitly left undeployed
- [ ] New or changed data screens: entry motion, press feedback, one flourish,
      reduced-motion path, snapshots re-recorded on the settled state
- [ ] Docs updated where behaviour changed (`docs/`, the platform README, `CLAUDE.md` rows)

## Where things are

| Path | What |
|---|---|
| `crates/core` | Pure domain logic + the UniFFI surface both apps use |
| `crates/server` | axum API, sqlx, retention, static-file serving of the dashboard |
| `web/` | React dashboard, generated API types, typed locale dictionaries |
| `android/` | Child agent (Kotlin/Compose), `ParentSetup` for code-free enrolment |
| `ios/` | One app, two roles; everything testable lives in `SchirmziitKit` |
| `copy/errors.toml` | The one source for every error message, in all four languages |
| `site/` | Astro product and docs site |
| `e2e/` | Maestro journeys + `seed.nu` for a throwaway instance |
| `deploy/` | docker-compose for self-hosters, change into ../home-network to run deploy commands |
