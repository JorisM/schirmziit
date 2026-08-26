# Error handling: every error surfaced, traceable, reportable

Date: 2026-08-26
Status: design approved, implementation plan pending

## Goal

Every error a user sees carries a stable code and a short reference, rendered on
screen so a screenshot alone is enough to identify what failed and which occurrence
it was. Server-side occurrences are findable by that reference in the server log.
Client-only failures stay on the device in a ring buffer and are copyable as text.

Nothing is transmitted anywhere automatically. No telemetry, no phone-home. This
follows the product's self-hosting stance: a family's data, including its failures,
stays on the family's own hardware.

## Current state (2026-08-26)

- `crates/server/src/error.rs` — `ApiError` produces RFC7807 `application/problem+json`
  with a stable `type` slug. Good bones. No code, no reference, no request id.
  `tracing::error!` fires only on 500; there is no request-level log line at all.
- Errors axum produces itself — 404 fallback, 405, `DefaultBodyLimit` rejection,
  JSON deserialize rejection — are not problem bodies and carry nothing.
- `web/src/api/client.ts` maps a problem into `ApiError`; when the body is not JSON
  it synthesises `{title: 'error', detail: statusText}`.
- Screens render either `t.errors.generic` (three strings exist: generic, notFound,
  offline) or the server's raw English `detail` — `web/src/pages/ChildDetail.tsx:50`
  and `ios/Sources/Views/ChildrenView.swift:74` both put English into a de/fr/it UI.
  That is an existing violation of the four-languages invariant.
- iOS: `ApiError { problem, transport, notConfigured }`; every view holds its own
  `errorText: String?` with ad-hoc copy.
- Android agent: errors are invisible. `SyncWorker` maps `outcome.error != null` to
  `Result.retry()` and drops the reason; `StatusScreen` shows only a last-sync age.
  A parent seeing "over a day" learns nothing about why.
- `crates/core`: `CoreError`/`FfiError` have two variants, no codes.

## Decisions

| Question | Decision |
|---|---|
| What "reporting" means | Copyable text block + server-side log lookup by reference; a screenshot must be enough to extract the relevant identifiers |
| What is rendered on screen | `SZ-E504 · 7f3a9c` on a tappable mono line; tapping expands version, endpoint, HTTP status, timestamp |
| Who owns the catalog | `crates/core` — one `ErrorCode` enum covering server and client-only codes |
| Client-only errors | Stay local in a ring buffer; never transmitted, no new endpoint |
| Scope | All four surfaces in one spec: core, server, web, iOS, Android |
| Copy volume | One message per code, in all four languages — authored once, generated per surface |
| Reaching every error site | Approach A: a typed error value threaded from the boundary to the view; string-typed error state is removed |

## 1. The catalog

`SZ-Ennn`, grouped by family so a screenshot reads without a lookup.

| Range | Family | Codes |
|---|---|---|
| 1xx | auth / session | 101 invalid-credentials · 102 unauthenticated · 103 session-expired · 104 registration-disabled · 105 email-taken · 106 wrong-parent-password |
| 2xx | scope / existence | 201 not-found · 202 child-not-found · 203 device-not-enrolled · 204 pairing-code-invalid · 205 pairing-code-expired |
| 3xx | request shape | 301 validation-failed · 302 payload-too-large · 303 unsupported-schema · 304 rate-limited |
| 5xx | transport (client-only) | 501 offline · 502 timeout · 503 tls-failed · 504 bad-response-body · 505 server-unreachable · 506 base-url-not-configured |
| 6xx | platform permission (client-only) | 601 usage-access-revoked · 602 notification-permission-missing · 603 media-notification-access-missing · 604 screen-time-authorisation-denied · 605 background-refresh-disabled |
| 7xx | local storage / decode (client-only) | 701 keychain-read-failed · 702 keychain-write-failed · 703 local-decode-failed · 704 queue-write-failed · 705 core-unknown-timezone · 706 core-malformed-json · 707 unexpected-client-error |
| 9xx | server-side | 901 internal · 902 database-unavailable |

Server-side codes are 9xx, not 4xx, so `SZ-E401` cannot be misread as HTTP 401 in a
screenshot. 4xx is left permanently unused.

`504 bad-response-body` is the captcha/proxy-page case from invariant 3: an
unparseable response must throw, never read as "all accepted".

Rules:

- Numbers are never reused. A retired code stays retired. The retired list is a
  `RETIRED: &[u16]` const next to the enum in `crates/core`, and the catalog test
  asserts no live variant uses a number in it.
- `crates/core` holds `ErrorCode` with both server and client-only variants, so a
  client cannot invent a code the catalog does not know.
- `FfiError` keeps its two variants and gains a `code()` accessor; 705 and 706 map
  onto them.
- Some codes are unreachable on some platforms (603 is Android-only, 604 iOS-only).
  That is intended. The parity test asserts copy exists everywhere, never that every
  platform can emit every code.

### The reference

- The server generates a request-id uuid v4 per request. The on-screen reference is
  its first 6 hex characters. The log prints `ref=7f3a9c request_id=<full uuid>`, so
  `grep 7f3a9c` on a pod finds the line.
- Client-only errors generate a local 6-hex reference. It lives only in that client's
  ring buffer — deliberate, given nothing is transmitted.
- Every response carries `x-request-id`, successes included, so a slow-but-successful
  request is traceable too.

## 2. Server

### Request id

`tower-http` gains the `request-id` feature. `SetRequestIdLayer` (uuid v4) and
`PropagateRequestIdLayer` wrap the whole router. CORS `expose_headers` must list
`x-request-id` or browser JS cannot read it on a success; error responses carry the
reference in the body regardless.

### The problem body becomes a type

The hand-rolled `serde_json::json!()` in `error.rs` becomes a `Problem` struct with
`ToSchema`, registered in `openapi.rs`:

```rust
struct Problem {
    r#type: String,   // unchanged, stable contract
    title: String,    // unchanged
    status: u16,
    detail: String,   // for logs and the copy-details block only
    code: ErrorCode,  // new — utoipa emits a string enum
    r#ref: String,    // new — 6 hex
}
```

`code` as an enum in `openapi.json` gives web its generated union in `schema.d.ts`
through the existing type-parity chain. No separate generated code file is needed.
`gen-check` fails if a code is added without regenerating. Kotlin and Swift get the
same enum via UniFFI.

### Attaching the reference, and normalising foreign errors

`ApiError::into_response` cannot reach the request extensions, and threading an
extractor through every handler is invasive. Instead one `normalize_errors`
middleware sits outermost. For any response with status >= 400 it:

1. reads the request id from extensions;
2. if the body is `application/problem+json`, fills in `ref`;
3. if the body is **not** a problem body — axum's 404 fallback, 405, the
   `DefaultBodyLimit` rejection, a JSON deserialize rejection — replaces it with a
   `Problem`, mapping status to a code.

Step 3 is what makes "every error" true rather than "every error someone remembered
to type". Bodies are tiny, so parse-and-reserialize costs nothing measurable.

### Logging

`TraceLayer` with a span carrying `ref`. One line per request: method, path, status,
latency, `code`, `ref`, full `request_id`. 2xx at `info`, 4xx at `warn`, 5xx at
`error`.

Never the body, never the email. `invalid-credentials` logs the code and reference
only — otherwise the log becomes an account-enumeration list.

### Tenancy

Invariant 2 requires another family's id to return 404, not 403. The code for a
scoped miss is therefore `SZ-E201 not-found`, identical to a genuine miss. A distinct
"wrong family" code would leak existence through the catalog itself.
`crates/server/tests/tenancy.rs` asserts it.

## 3. Client plumbing

Shared shape, three implementations:

```
AppError { code, ref, at, endpoint?, httpStatus?, appVersion, platform }
```

Constructed only at boundaries, never in a view. String-typed error state
(`errorText: String?`, `useState<string | null>`) is removed, so a screen cannot
render an error without a code.

| Surface | Boundary | Maps to |
|---|---|---|
| web | `client.ts request()` | problem body -> its code/ref; fetch reject -> 501/502; non-JSON body -> 504 |
| web | ErrorBoundary, `window.onerror`, `unhandledrejection` | 707 |
| iOS | `ApiClient.send` | same three cases; `.notConfigured` -> 506 |
| iOS | keychain wrapper, Screen Time authorisation | 701/702, 604 |
| Android | `Collector.kt:127` catch, `SyncWorker` | transport 5xx, 601/603, 704 |
| all | `FfiError` from core | 705/706 |

### Ring buffer

50 entries, written at construction time. It is a sink, not a store the UI reads
from — the error value still travels to the screen that asked for the work, so there
is no scoping ambiguity about which screen shows which failure.

One deliberate asymmetry: the Android agent is a background collector that is killed
constantly, so an in-memory buffer would be empty by the time a parent opens
`StatusScreen`. The agent persists its last 10 in DataStore; the iOS agent side does
the same. Parent-facing apps and web keep it in memory only, so the web buffer dies
on reload. Acceptable — the screenshot is the primary report path and copy-details is
used in the moment.

### Copy-details payload

Identical text on all four surfaces:

```
SZ-E504 · 7f3a9c
2026-08-26 14:02:11 +02:00
schirmziit 0.4.1 · ios 26.0 · iPhone15,2
GET /v1/children → 502
```

When the buffer holds more, a second block lists the previous 4 entries one line
each — the "it has been failing all morning" case a single screenshot hides.

Never included: email, child name, package names, request or response bodies. The
endpoint is a **path only, never the host** — a self-hoster pasting a screenshot into
a public issue would otherwise publish their homelab hostname.

### Version plumbing

Does not exist on any surface today. `VITE_APP_VERSION` at build for web,
`CFBundleShortVersionString` on iOS, `BuildConfig.VERSION_NAME` on Android,
`CARGO_PKG_VERSION` on the server. Four small pieces of build wiring, not free.

## 4. Error UI and motion

One component per surface: `ErrorPanel` (web), `ErrorView` (iOS), `ErrorCard`
(Android). Same anatomy:

```
  ⚠  Die Daten konnten nicht geladen werden.        <- what happened
     Prüf deine Verbindung und versuch es nochmal.  <- what to do
     [ Erneut versuchen ]                           <- one action, or none
     SZ-E504 · 7f3a9c                           ▾   <- tappable
     └ 0.4.1 · GET /v1/children → 502
       26.08.2026, 14:02:11 · [ Details kopieren ]
```

### Two placements

- **Inline** — the data failed to load. The panel takes the footprint the skeleton
  had, so nothing jumps.
- **Banner** — a *refresh* failed while good data is already on screen. The data
  stays; the banner says it is stale. Blanking a loaded chart because a poll failed
  is the same class of mistake as invariant 3's "never lose a day", one layer up.

### Motion

The panel crossfades in over its own skeleton, the same path data takes,
`--motion-*` tokens, 200–400 ms. The disclosure is a height and opacity transition.
Press feedback (`--motion-fast`) on the retry button and on the mono line, which is
tappable.

**No flourish on an error state.** A deliberate exception to one-flourish-per-screen:
the flourish belongs to the data. Animating a failure is the interface enjoying
itself at the parent's expense. `CLAUDE.md`'s Feel section is updated to say so,
since as written the rule reads as unconditional.

Reduced motion lands on the final state instantly, panel and disclosure both.

The Android agent stays motion-free; its `ErrorCard` is static, per its battery
budget. Its job is telling a parent why sync stopped, which `StatusScreen` cannot do
today.

### Colour

Two weights, because not everything is `--urgent`:

- **urgent** (red): something broke and needs attention — 9xx, 7xx, 3xx.
- **neutral** (muted): expected, self-correcting, or waiting on the user — 501
  offline, 6xx permission-not-yet-granted, 304 rate-limited.

An offline phone in a Swiss valley painting the dashboard red trains a parent to
ignore red.

The mono line is dimmed **by token, not by opacity**, and holds >= 4.5:1. `opacity:
0.5` over red is how it becomes unreadable exactly when someone needs to read it —
and it has to survive a screenshot.

### Accessibility and child-facing copy

`role="alert"` stays; the disclosure gets `aria-expanded`/`aria-controls`. iOS reads
the code as characters (`S Z E five zero four`), not "SZE504".

The child-facing app uses the same component, du-form, and never implies the child
caused the failure. The Beratung 147 link stays wherever it is today.

## 5. Copy and parity

35 codes x 2 lines x 4 languages = 280 strings. Authored per surface that is four
dictionaries saying nearly the same thing, which drift.

**Author once, generate four.** `copy/errors.toml` at the repo root is the source:

```toml
[E504]
weight = "urgent"
reach  = ["web", "ios", "android"]
de.title  = "Der Server hat unerwartet geantwortet."
de.action = "Versuch es nochmal. Bleibt es so, prüf, ob dein Server erreichbar ist."
# fr, it, en
```

`just gen` emits, and `gen-check` verifies against git exactly as it does for
`schema.d.ts`:

- `web/src/i18n/errors.<locale>.ts` — typed, folded into the existing dictionaries
- `ios/Sources/Resources/<locale>.lproj/ErrorCopy.strings`
- `android/app/src/main/res/values-<locale>/error_copy.xml`

This is the only machinery added beyond section 2: the type-parity chain carries the
codes, but not their translations.

**Reach manifests.** `reach` declares which surfaces can emit a code. The parity test
asserts every code has all four languages, and every surface has copy for every code
it declares reachable. Adding a code forces an explicit per-surface decision instead
of a silent gap.

**Platform action overrides.** "Open Settings" differs per platform. An optional
`[E601.android] action = "..."` overrides the shared line where it must.

**Copy rules apply unchanged**: plain language, what happened and what to do,
du-form where the child reads it. The existing tests that fail on "heimlich",
"sneak", "en cachette", "di nascosto" must scan the generated dictionaries too — a
dictionary the copy tests do not read is a hole.

**`detail` is never rendered.** After this change the server's `detail` field exists
for logs and the copy-details block only. A test asserts no view reads it, which
closes the existing English-in-a-German-UI bug at `ChildDetail.tsx:50` and
`ChildrenView.swift:74`.

## 6. Testing

TDD: test first, watch it fail for the right reason, then implement.

**Core**
- Every `ErrorCode` has a unique number; the retired-numbers list is never re-issued.
- Every variant has an entry in `copy/errors.toml`, and every TOML key is a known
  code — both directions, or a typo'd key silently produces no copy.
- `FfiError` -> code mapping.

**Server**
- Table test over every `ApiError` variant: status, `code`, `ref` present.
- Normalisation: unknown path, wrong method, oversized body, malformed JSON all come
  back `application/problem+json` with a code and a reference.
- `x-request-id` on success and on error; the body `ref` is the header uuid's first
  6 characters.
- The log line contains the reference — captured with a test subscriber.
- The `invalid-credentials` log line contains no email.
- Tenancy: another family's child returns `SZ-E201`, identical to a genuine miss.
- CORS exposes `x-request-id` — in `crates/server/tests/cors.rs`.

**Web**
- Client mapping, extending the existing non-JSON test at `client.test.ts:50` to
  assert `SZ-E504`.
- Ring buffer FIFO cap.
- `ErrorPanel` collapsed and expanded; exact copy-details string.
- Banner placement keeps stale data mounted.
- Reduced-motion settled state.
- Generated-dictionary parity; no view reads `problem.detail`.

**iOS** — `SchirmziitKit` unit tests for mapping and payload; snapshots for
collapsed, expanded, banner, reduced motion, parent and child copy.

**Android** — Roborazzi for `ErrorCard` states; `Collector.kt:127` catch mapping;
DataStore last-10 survives simulated process death.

### Mutations that must go red

| Break | Test that must fail |
|---|---|
| Drop `ref` from the problem body | server table test |
| Let a failed refresh clear loaded data | web banner test |
| Delete one locale from `errors.toml` | parity test |
| Put the host in the copy payload | payload test |
| Re-issue a retired code number | catalog test |
| Emit a distinct code for a cross-family miss | tenancy test |

### What cannot be honestly tested

Stated rather than covered by a test that passes while the phone is broken:

- That a screenshot is legible. The contrast ratio on the mono token is assertable;
  how it survives a messenger's re-compression is not.
- That `grep 7f3a9c` finds the line on the live pod. Verified manually against the
  deployed instance.
- iOS Screen Time authorisation denial (604) — entitlement-gated, no simulator path.
  The mapping is testable; the real denial is not.
- Clipboard writes. The payload string is asserted; the system clipboard is not.
- Real offline behaviour of `URLSession` and OkHttp. Only the mapping from injected
  transport errors is testable.

## Definition of done

- [ ] Tests written first; the six mutations above run and go red
- [ ] All strings in de/fr/it/en, generated from `copy/errors.toml`; parity green
- [ ] Type-parity chain regenerated and committed (`just openapi`, `just gen`)
- [ ] iOS and Android snapshots re-recorded deliberately and looked at
- [ ] All five gates green
- [ ] Deployed and verified: trigger a 500 on the running instance, read the
      reference off the response, find it with `grep` in the pod log
- [ ] `CLAUDE.md` updated: no-flourish-on-errors exception, `copy/errors.toml` row
- [ ] `docs/` gets the catalog reference table; platform READMEs get the ring-buffer
      note

## Out of scope

- Any transmission of client errors, including opt-in telemetry and a
  `POST /v1/client-errors` endpoint. Decided against: the ring buffer plus a
  screenshot is the report path.
- A diagnostics screen listing past errors. The copy-details block already carries
  the last five.
- Blocking, time limits, or anything that changes what the product does — unrelated.
