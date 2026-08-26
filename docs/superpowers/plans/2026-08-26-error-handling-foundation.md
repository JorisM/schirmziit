# Error Handling Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every API error carries a stable `SZ-Ennn` code and a 6-hex reference that appears in the server log, and the copy for every code exists in all four languages, generated from one source.

**Architecture:** `crates/core` owns an `ErrorCode` enum shared by the server (Rust), the apps (UniFFI) and the dashboard (via `openapi.json` -> `schema.d.ts`). The server gains a request-id layer, a typed `Problem` response body, an outermost middleware that normalises errors axum produced itself, and a per-request log line. A new `crates/copygen` binary turns `copy/errors.toml` into per-surface dictionaries that later plans consume.

**Tech Stack:** Rust 2024, axum, tower-http (`request-id`, `trace`), utoipa 5, uniffi 0.32, sqlx, `toml` for the copy source, `just` for the generation gates.

**Spec:** `docs/superpowers/specs/2026-08-26-error-handling-design.md`

## Global Constraints

- Code format is `SZ-Ennn`. Numbers are **never reused**; retired numbers stay retired.
- Server-side codes are **9xx**, not 4xx — `SZ-E401` must never be mistakable for HTTP 401. The 4xx range is permanently unused.
- Four languages, always: `de` (Schweizer Hochdeutsch, no ß, du-form), `fr`, `it`, `en`. Every string lands in all four in the same commit.
- Copy rules: say what happened and what to do. Never imply secrecy — the words "heimlich", "sneak", "en cachette", "di nascosto" are forbidden in any locale and existing tests enforce it.
- The wire format lives in `crates/core` only (invariant 1). `crates/core` must not gain dependencies the Android build would have to carry: the copy generator is a **separate crate**, never a module of core.
- Tenancy (invariant 2): another family's id returns 404 with code `SZ-E201`, byte-identical to a genuine miss. Never a distinct code.
- `detail` in the problem body is for logs and the copy-details block only. It is never rendered as a user-facing message.
- Logs never contain an email, a child name, a package name, or a request/response body.
- Gate after every task: `just rust-check`. After Task 3 also `just web-check`. `bin/check` runs every gate and keeps going after the first failure — useful at the end of a task, not between steps. Every `bin/*` script re-enters the nix dev shell itself, so no special terminal is needed.
- Commit messages follow `type: subject` + blank line + `refs: SZ-ERRORS`. No AI-attribution trailers.

---

## File Structure

**Create**
- `crates/core/src/codes.rs` — the `ErrorCode` enum, its numbers, the retired list.
- `crates/copygen/Cargo.toml`, `crates/copygen/src/main.rs` — reads `copy/errors.toml`, validates it against `ErrorCode`, writes the three per-surface dictionaries.
- `copy/errors.toml` — the single copy source for all 35 codes in four languages.
- `crates/server/src/request_id.rs` — request-id layer wiring and the `Ref` newtype.
- `crates/server/src/normalize.rs` — the outermost middleware that fills in `ref` and converts foreign error responses into problem bodies.
- `crates/server/tests/problem.rs` — the table test over every `ApiError`, plus normalisation tests.
- `docs/error-codes.md` — the catalog as a reference table.

**Modify**
- `crates/core/src/lib.rs` — `pub mod codes;`
- `crates/core/src/error.rs` / `ffi.rs` — `code()` accessors mapping onto 705/706.
- `crates/server/src/error.rs` — `ApiError::code()`, the `Problem` struct, `IntoResponse` emitting it.
- `crates/server/src/openapi.rs` — register `Problem` and `ErrorCode`.
- `crates/server/src/lib.rs` — layer order: request id (outermost), normalise, trace, then the existing routers.
- `crates/server/src/main.rs` — tracing subscriber format.
- `crates/server/Cargo.toml` — `tower-http` gains `request-id`.
- `crates/server/tests/cors.rs` — `x-request-id` is exposed.
- `crates/server/tests/tenancy.rs` — cross-family miss returns `SZ-E201`.
- `justfile` — `gen-copy`, `gen-copy-check`, folded into `gen-check`.
- `Cargo.toml` — workspace members gains `crates/copygen`.
- `CLAUDE.md` — Where-things-are rows, the Feel exception.

---

### Task 1: The `ErrorCode` catalog in core

**Files:**
- Create: `crates/core/src/codes.rs`
- Modify: `crates/core/src/lib.rs`
- Test: `crates/core/src/codes.rs` (inline `#[cfg(test)] mod tests`, matching the crate's existing style)

**Interfaces:**
- Consumes: nothing.
- Produces: `schirmziit_core::codes::ErrorCode` (a `Copy` unit-variant enum), `ErrorCode::number(self) -> u16`, `ErrorCode::as_str(self) -> &'static str`, `ErrorCode::ALL: &[ErrorCode]`, `codes::RETIRED: &[u16]`. Serialises as its `SZ-Ennn` string. Every later task and every later plan refers to variants by these names.

- [ ] **Step 1: Write the failing test**

Create `crates/core/src/codes.rs` containing only the test module for now:

```rust
#[cfg(test)]
mod tests {
    use super::*;

    /// Two codes sharing a number would make a reference ambiguous in a log.
    #[test]
    fn every_number_is_unique() {
        let mut seen = std::collections::HashSet::new();
        for code in ErrorCode::ALL {
            assert!(
                seen.insert(code.number()),
                "{} reuses number {}",
                code.as_str(),
                code.number()
            );
        }
    }

    /// The rendered string is what a parent reads off a screenshot; it must
    /// agree with the number a log line is grepped by.
    #[test]
    fn the_string_and_the_number_agree() {
        for code in ErrorCode::ALL {
            assert_eq!(code.as_str(), format!("SZ-E{}", code.number()));
        }
    }

    /// A retired number must never come back meaning something else: an old
    /// screenshot would then describe a different failure.
    #[test]
    fn no_live_code_uses_a_retired_number() {
        for code in ErrorCode::ALL {
            assert!(
                !RETIRED.contains(&code.number()),
                "{} uses retired number {}",
                code.as_str(),
                code.number()
            );
        }
    }

    /// 4xx is reserved so SZ-E401 can never be read as HTTP 401.
    #[test]
    fn the_four_hundreds_are_never_used() {
        for code in ErrorCode::ALL {
            assert!(
                !(400..500).contains(&code.number()),
                "{} sits in the reserved 4xx range",
                code.as_str()
            );
        }
    }

    #[test]
    fn it_serialises_as_its_wire_string() {
        let json = serde_json::to_string(&ErrorCode::Offline).unwrap();
        assert_eq!(json, "\"SZ-E501\"");
    }
}
```

Add `pub mod codes;` to `crates/core/src/lib.rs`, alphabetically after `buckets`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cargo test -p schirmziit-core codes`
Expected: FAIL to compile — `cannot find type ErrorCode in this scope`.

- [ ] **Step 3: Write minimal implementation**

Above the test module in `crates/core/src/codes.rs`:

```rust
//! The error catalog. One enum for the whole product: the server, both apps and
//! the dashboard name a failure with the same code, so a screenshot identifies
//! it without anyone having to ask which screen it came from.
//!
//! `SZ-Ennn`, grouped by family so the range alone is informative:
//!
//! | Range | Family |
//! |---|---|
//! | 1xx | auth / session |
//! | 2xx | scope / existence |
//! | 3xx | request shape |
//! | 5xx | transport, client-only |
//! | 6xx | platform permission, client-only |
//! | 7xx | local storage / decode, client-only |
//! | 9xx | server-side |
//!
//! 4xx is deliberately empty: `SZ-E401` next to an HTTP status would read as
//! "unauthorised" to everyone who has ever seen a 401, and it would mean
//! "internal server error". Server-side codes are 9xx instead.
//!
//! Client-only codes live here rather than in each app so a client cannot
//! invent a code the catalog does not know, and so `copy/errors.toml` has one
//! list to satisfy.

/// Numbers that once meant something else. Never re-issue one: an old
/// screenshot would then describe a different failure than it did on the day it
/// was taken.
pub const RETIRED: &[u16] = &[];

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "schema", derive(utoipa::ToSchema))]
#[derive(uniffi::Enum)]
pub enum ErrorCode {
    // 1xx — auth / session
    #[serde(rename = "SZ-E101")]
    InvalidCredentials,
    #[serde(rename = "SZ-E102")]
    Unauthenticated,
    #[serde(rename = "SZ-E103")]
    SessionExpired,
    #[serde(rename = "SZ-E104")]
    RegistrationDisabled,
    #[serde(rename = "SZ-E105")]
    EmailTaken,
    #[serde(rename = "SZ-E106")]
    WrongParentPassword,

    // 2xx — scope / existence
    #[serde(rename = "SZ-E201")]
    NotFound,
    #[serde(rename = "SZ-E202")]
    ChildNotFound,
    #[serde(rename = "SZ-E203")]
    DeviceNotEnrolled,
    #[serde(rename = "SZ-E204")]
    PairingCodeInvalid,
    #[serde(rename = "SZ-E205")]
    PairingCodeExpired,

    // 3xx — request shape
    #[serde(rename = "SZ-E301")]
    ValidationFailed,
    #[serde(rename = "SZ-E302")]
    PayloadTooLarge,
    #[serde(rename = "SZ-E303")]
    UnsupportedSchema,
    #[serde(rename = "SZ-E304")]
    RateLimited,

    // 5xx — transport, client-only
    #[serde(rename = "SZ-E501")]
    Offline,
    #[serde(rename = "SZ-E502")]
    Timeout,
    #[serde(rename = "SZ-E503")]
    TlsFailed,
    #[serde(rename = "SZ-E504")]
    BadResponseBody,
    #[serde(rename = "SZ-E505")]
    ServerUnreachable,
    #[serde(rename = "SZ-E506")]
    BaseUrlNotConfigured,

    // 6xx — platform permission, client-only
    #[serde(rename = "SZ-E601")]
    UsageAccessRevoked,
    #[serde(rename = "SZ-E602")]
    NotificationPermissionMissing,
    #[serde(rename = "SZ-E603")]
    MediaNotificationAccessMissing,
    #[serde(rename = "SZ-E604")]
    ScreenTimeAuthorisationDenied,
    #[serde(rename = "SZ-E605")]
    BackgroundRefreshDisabled,

    // 7xx — local storage / decode, client-only
    #[serde(rename = "SZ-E701")]
    KeychainReadFailed,
    #[serde(rename = "SZ-E702")]
    KeychainWriteFailed,
    #[serde(rename = "SZ-E703")]
    LocalDecodeFailed,
    #[serde(rename = "SZ-E704")]
    QueueWriteFailed,
    #[serde(rename = "SZ-E705")]
    CoreUnknownTimezone,
    #[serde(rename = "SZ-E706")]
    CoreMalformedJson,
    #[serde(rename = "SZ-E707")]
    UnexpectedClientError,

    // 9xx — server-side
    #[serde(rename = "SZ-E901")]
    Internal,
    #[serde(rename = "SZ-E902")]
    DatabaseUnavailable,
}

impl ErrorCode {
    /// Every variant, in catalog order. The tests and `crates/copygen` walk
    /// this, so a new variant is covered by both the moment it is added.
    pub const ALL: &'static [ErrorCode] = &[
        ErrorCode::InvalidCredentials,
        ErrorCode::Unauthenticated,
        ErrorCode::SessionExpired,
        ErrorCode::RegistrationDisabled,
        ErrorCode::EmailTaken,
        ErrorCode::WrongParentPassword,
        ErrorCode::NotFound,
        ErrorCode::ChildNotFound,
        ErrorCode::DeviceNotEnrolled,
        ErrorCode::PairingCodeInvalid,
        ErrorCode::PairingCodeExpired,
        ErrorCode::ValidationFailed,
        ErrorCode::PayloadTooLarge,
        ErrorCode::UnsupportedSchema,
        ErrorCode::RateLimited,
        ErrorCode::Offline,
        ErrorCode::Timeout,
        ErrorCode::TlsFailed,
        ErrorCode::BadResponseBody,
        ErrorCode::ServerUnreachable,
        ErrorCode::BaseUrlNotConfigured,
        ErrorCode::UsageAccessRevoked,
        ErrorCode::NotificationPermissionMissing,
        ErrorCode::MediaNotificationAccessMissing,
        ErrorCode::ScreenTimeAuthorisationDenied,
        ErrorCode::BackgroundRefreshDisabled,
        ErrorCode::KeychainReadFailed,
        ErrorCode::KeychainWriteFailed,
        ErrorCode::LocalDecodeFailed,
        ErrorCode::QueueWriteFailed,
        ErrorCode::CoreUnknownTimezone,
        ErrorCode::CoreMalformedJson,
        ErrorCode::UnexpectedClientError,
        ErrorCode::Internal,
        ErrorCode::DatabaseUnavailable,
    ];

    pub const fn number(self) -> u16 {
        match self {
            ErrorCode::InvalidCredentials => 101,
            ErrorCode::Unauthenticated => 102,
            ErrorCode::SessionExpired => 103,
            ErrorCode::RegistrationDisabled => 104,
            ErrorCode::EmailTaken => 105,
            ErrorCode::WrongParentPassword => 106,
            ErrorCode::NotFound => 201,
            ErrorCode::ChildNotFound => 202,
            ErrorCode::DeviceNotEnrolled => 203,
            ErrorCode::PairingCodeInvalid => 204,
            ErrorCode::PairingCodeExpired => 205,
            ErrorCode::ValidationFailed => 301,
            ErrorCode::PayloadTooLarge => 302,
            ErrorCode::UnsupportedSchema => 303,
            ErrorCode::RateLimited => 304,
            ErrorCode::Offline => 501,
            ErrorCode::Timeout => 502,
            ErrorCode::TlsFailed => 503,
            ErrorCode::BadResponseBody => 504,
            ErrorCode::ServerUnreachable => 505,
            ErrorCode::BaseUrlNotConfigured => 506,
            ErrorCode::UsageAccessRevoked => 601,
            ErrorCode::NotificationPermissionMissing => 602,
            ErrorCode::MediaNotificationAccessMissing => 603,
            ErrorCode::ScreenTimeAuthorisationDenied => 604,
            ErrorCode::BackgroundRefreshDisabled => 605,
            ErrorCode::KeychainReadFailed => 701,
            ErrorCode::KeychainWriteFailed => 702,
            ErrorCode::LocalDecodeFailed => 703,
            ErrorCode::QueueWriteFailed => 704,
            ErrorCode::CoreUnknownTimezone => 705,
            ErrorCode::CoreMalformedJson => 706,
            ErrorCode::UnexpectedClientError => 707,
            ErrorCode::Internal => 901,
            ErrorCode::DatabaseUnavailable => 902,
        }
    }

    /// What a parent sees, and what the log is grepped by.
    pub const fn as_str(self) -> &'static str {
        match self {
            ErrorCode::InvalidCredentials => "SZ-E101",
            ErrorCode::Unauthenticated => "SZ-E102",
            ErrorCode::SessionExpired => "SZ-E103",
            ErrorCode::RegistrationDisabled => "SZ-E104",
            ErrorCode::EmailTaken => "SZ-E105",
            ErrorCode::WrongParentPassword => "SZ-E106",
            ErrorCode::NotFound => "SZ-E201",
            ErrorCode::ChildNotFound => "SZ-E202",
            ErrorCode::DeviceNotEnrolled => "SZ-E203",
            ErrorCode::PairingCodeInvalid => "SZ-E204",
            ErrorCode::PairingCodeExpired => "SZ-E205",
            ErrorCode::ValidationFailed => "SZ-E301",
            ErrorCode::PayloadTooLarge => "SZ-E302",
            ErrorCode::UnsupportedSchema => "SZ-E303",
            ErrorCode::RateLimited => "SZ-E304",
            ErrorCode::Offline => "SZ-E501",
            ErrorCode::Timeout => "SZ-E502",
            ErrorCode::TlsFailed => "SZ-E503",
            ErrorCode::BadResponseBody => "SZ-E504",
            ErrorCode::ServerUnreachable => "SZ-E505",
            ErrorCode::BaseUrlNotConfigured => "SZ-E506",
            ErrorCode::UsageAccessRevoked => "SZ-E601",
            ErrorCode::NotificationPermissionMissing => "SZ-E602",
            ErrorCode::MediaNotificationAccessMissing => "SZ-E603",
            ErrorCode::ScreenTimeAuthorisationDenied => "SZ-E604",
            ErrorCode::BackgroundRefreshDisabled => "SZ-E605",
            ErrorCode::KeychainReadFailed => "SZ-E701",
            ErrorCode::KeychainWriteFailed => "SZ-E702",
            ErrorCode::LocalDecodeFailed => "SZ-E703",
            ErrorCode::QueueWriteFailed => "SZ-E704",
            ErrorCode::CoreUnknownTimezone => "SZ-E705",
            ErrorCode::CoreMalformedJson => "SZ-E706",
            ErrorCode::UnexpectedClientError => "SZ-E707",
            ErrorCode::Internal => "SZ-E901",
            ErrorCode::DatabaseUnavailable => "SZ-E902",
        }
    }
}

impl std::fmt::Display for ErrorCode {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.as_str())
    }
}
```

Note on the derives: `uniffi::Enum` is what makes the catalog reachable from Kotlin and Swift in the later app plans. `ToSchema` is feature-gated exactly like `crates/core/src/wire.rs` does it, so the Android build never pulls utoipa in.

- [ ] **Step 4: Run test to verify it passes**

Run: `cargo test -p schirmziit-core codes`
Expected: PASS, 5 tests.

- [ ] **Step 5: Prove the tests are not vacuous**

Temporarily change `ErrorCode::Timeout => 502` to `=> 501` in `number()`.
Run: `cargo test -p schirmziit-core codes`
Expected: `every_number_is_unique` FAILS with "SZ-E502 reuses number 501", and `the_string_and_the_number_agree` FAILS too. Revert the change.

Then temporarily set `RETIRED` to `&[501]`.
Expected: `no_live_code_uses_a_retired_number` FAILS. Revert.

- [ ] **Step 6: Add the `code()` accessors on the core error types**

In `crates/core/src/error.rs`:

```rust
use crate::codes::ErrorCode;

impl CoreError {
    pub fn code(&self) -> ErrorCode {
        match self {
            CoreError::UnknownTimezone(_) => ErrorCode::CoreUnknownTimezone,
            CoreError::BadJson(_) => ErrorCode::CoreMalformedJson,
        }
    }
}
```

In `crates/core/src/ffi.rs`, next to the existing `From<CoreError> for FfiError`:

```rust
#[uniffi::export]
pub fn ffi_error_code(error: &FfiError) -> ErrorCode {
    match error {
        FfiError::UnknownTimezone { .. } => ErrorCode::CoreUnknownTimezone,
        FfiError::BadJson { .. } => ErrorCode::CoreMalformedJson,
    }
}
```

A free function rather than a method: `FfiError` is a uniffi *error* type, and uniffi does not export inherent methods on those. Both apps call `ffiErrorCode(error)` in their plans.

Add to the same inline test module:

```rust
#[test]
fn core_errors_carry_their_codes() {
    assert_eq!(
        CoreError::UnknownTimezone("Mars/Olympus".into()).code(),
        ErrorCode::CoreUnknownTimezone
    );
    assert_eq!(CoreError::BadJson("{".into()).code(), ErrorCode::CoreMalformedJson);
}
```

(Place this test in `crates/core/src/error.rs`'s own `#[cfg(test)]` module, importing `super::*` and `crate::codes::ErrorCode`.)

- [ ] **Step 7: Run the full core gate**

Run: `cargo test -p schirmziit-core && cargo clippy --all-targets -- -D warnings && cargo fmt --check`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add crates/core/src/codes.rs crates/core/src/lib.rs crates/core/src/error.rs crates/core/src/ffi.rs
git commit -F - <<'MSG'
feat: one error catalog in core, shared by every surface

refs: SZ-ERRORS
MSG
```

---

### Task 2: `copy/errors.toml` and its validator

**Files:**
- Create: `copy/errors.toml`, `crates/copygen/Cargo.toml`, `crates/copygen/src/main.rs`, `crates/copygen/src/catalog.rs`
- Modify: `Cargo.toml` (workspace members)
- Test: `crates/copygen/src/catalog.rs` (inline tests)

**Interfaces:**
- Consumes: `schirmziit_core::codes::ErrorCode` from Task 1.
- Produces: `copygen::catalog::{Catalog, Entry, Message, Weight, Surface}` and `Catalog::load(path) -> Result<Catalog, String>`. Validation lives in the tests rather than a `validate()` method: the only caller is the generator, and a generator that runs on a bad catalog is a bug the gate should have caught first. Task 3 consumes `Catalog` to emit files.

- [ ] **Step 1: Create the crate skeleton**

`crates/copygen/Cargo.toml`:

```toml
[package]
name = "copygen"
edition.workspace = true
version.workspace = true
publish = false

[dependencies]
schirmziit-core = { path = "../core" }
serde.workspace = true
toml = "0.9"
```

Add `"crates/copygen"` to `members` in the root `Cargo.toml`.

Why a separate crate: `crates/core` is compiled into the Android `.so` and the iOS xcframework. A TOML parser has no business in either.

- [ ] **Step 2: Write the failing test**

`crates/copygen/src/catalog.rs`:

```rust
#[cfg(test)]
mod tests {
    use super::*;

    fn repo_catalog() -> Catalog {
        Catalog::load(concat!(env!("CARGO_MANIFEST_DIR"), "/../../copy/errors.toml"))
            .expect("copy/errors.toml parses")
    }

    /// A code with no copy renders as a blank error, which is worse than a
    /// wrong one: the parent sees an empty box and cannot report anything.
    #[test]
    fn every_code_has_an_entry() {
        let catalog = repo_catalog();
        for code in ErrorCode::ALL {
            assert!(
                catalog.entries.contains_key(code.as_str()),
                "{} has no entry in copy/errors.toml",
                code.as_str()
            );
        }
    }

    /// A typo'd key would otherwise sit in the file forever, silently
    /// translating nothing.
    #[test]
    fn every_entry_names_a_real_code() {
        let catalog = repo_catalog();
        let known: std::collections::HashSet<&str> =
            ErrorCode::ALL.iter().map(|c| c.as_str()).collect();
        for key in catalog.entries.keys() {
            assert!(known.contains(key.as_str()), "{key} is not a known code");
        }
    }

    /// Four languages, always — the same rule the dashboard dictionaries live by.
    #[test]
    fn every_entry_has_all_four_locales() {
        let catalog = repo_catalog();
        for (key, entry) in &catalog.entries {
            for locale in ["de", "fr", "it", "en"] {
                let copy = entry.locales.get(locale);
                assert!(copy.is_some(), "{key} is missing {locale}");
                let copy = copy.unwrap();
                assert!(!copy.title.trim().is_empty(), "{key}.{locale}.title is empty");
                assert!(!copy.action.trim().is_empty(), "{key}.{locale}.action is empty");
            }
        }
    }

    /// Same rule the app and the site are already held to.
    #[test]
    fn no_locale_implies_secrecy() {
        let catalog = repo_catalog();
        let forbidden = ["heimlich", "sneak", "en cachette", "di nascosto"];
        for (key, entry) in &catalog.entries {
            for (locale, copy) in &entry.locales {
                let haystack = format!("{} {}", copy.title, copy.action).to_lowercase();
                for word in forbidden {
                    assert!(
                        !haystack.contains(word),
                        "{key}.{locale} contains the forbidden word {word:?}"
                    );
                }
            }
        }
    }

    /// Schweizer Hochdeutsch has no ß.
    #[test]
    fn german_never_uses_eszett() {
        let catalog = repo_catalog();
        for (key, entry) in &catalog.entries {
            let de = entry.locales.get("de").unwrap();
            assert!(
                !de.title.contains('ß') && !de.action.contains('ß'),
                "{key}.de uses ß"
            );
        }
    }

    /// An unreachable code with no surface would generate into nothing.
    #[test]
    fn every_entry_reaches_at_least_one_surface() {
        let catalog = repo_catalog();
        for (key, entry) in &catalog.entries {
            assert!(!entry.reach.is_empty(), "{key} reaches no surface");
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cargo test -p copygen`
Expected: FAIL to compile — `Catalog` is not defined.

- [ ] **Step 4: Write the catalog types**

Above the tests in `crates/copygen/src/catalog.rs`:

```rust
use schirmziit_core::codes::ErrorCode;
use std::collections::BTreeMap;

/// How loudly a code is presented. Not every failure deserves red: an offline
/// phone is expected and self-correcting, and painting that alarming teaches a
/// parent to ignore the colour that means something is actually wrong.
#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Weight {
    Urgent,
    Neutral,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, serde::Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Surface {
    Web,
    Ios,
    Android,
}

/// Not named `Copy`: that shadows `std::marker::Copy` in a file that derives it
/// two types above, and the resulting error message is genuinely baffling.
#[derive(Debug, Clone, serde::Deserialize)]
pub struct Message {
    /// What happened, in the reader's terms.
    pub title: String,
    /// What to do about it. Never empty — an error with no next step is a
    /// dead end.
    pub action: String,
}

#[derive(Debug, Clone, serde::Deserialize)]
pub struct Entry {
    pub weight: Weight,
    /// Which surfaces can actually emit this code. `web` can never hit
    /// SZ-E603, and demanding it carry the string would be theatre.
    pub reach: Vec<Surface>,
    #[serde(flatten)]
    pub locales: BTreeMap<String, Message>,
}

#[derive(Debug, Clone, serde::Deserialize)]
pub struct Catalog {
    #[serde(flatten)]
    pub entries: BTreeMap<String, Entry>,
}

impl Catalog {
    pub fn load(path: impl AsRef<std::path::Path>) -> Result<Self, String> {
        let path = path.as_ref();
        let text = std::fs::read_to_string(path).map_err(|e| format!("{}: {e}", path.display()))?;
        toml::from_str(&text).map_err(|e| format!("{}: {e}", path.display()))
    }
}
```

`crates/copygen/src/main.rs` for now:

```rust
mod catalog;

fn main() {
    // Task 3 turns this into the generator.
    println!("copygen");
}
```

- [ ] **Step 5: Write `copy/errors.toml`**

All 35 codes. The de/fr/it/en text below is the starting draft — read it once as a whole before committing, because this is the copy a parent actually reads.

```toml
# The single source for every error message the product shows.
#
# `just gen-copy` turns this into the dashboard dictionary, the iOS .strings
# and the Android string resources. Never edit those generated files.
#
# weight: "urgent" (something broke, needs attention) or "neutral" (expected,
# self-correcting, or waiting on the user).
# reach:  which surfaces can emit the code at all.

[SZ-E101]
weight = "urgent"
reach = ["web", "ios", "android"]
de = { title = "E-Mail oder Passwort stimmt nicht.", action = "Probier es nochmal. Hast du das Passwort vergessen, setz es auf deinem Server zurueck." }
fr = { title = "L'e-mail ou le mot de passe est incorrect.", action = "Réessaie. Si tu as oublié ton mot de passe, réinitialise-le sur ton serveur." }
it = { title = "L'e-mail o la password non è corretta.", action = "Riprova. Se hai dimenticato la password, reimpostala sul tuo server." }
en = { title = "That email or password is wrong.", action = "Try again. If you forgot your password, reset it on your server." }
```

And one neutral entry with a narrower reach, so both variations are on the page:

```toml
[SZ-E603]
weight = "neutral"
reach = ["android"]
de = { title = "Hintergrund-Wiedergabe wird nicht gemessen.", action = "Gib in den Einstellungen den Zugriff auf Benachrichtigungen frei, damit Musik bei ausgeschaltetem Bildschirm mitgezaehlt wird." }
fr = { title = "L'écoute en arrière-plan n'est pas mesurée.", action = "Autorise l'accès aux notifications dans les réglages pour compter la musique écran éteint." }
it = { title = "L'ascolto in background non viene misurato.", action = "Consenti l'accesso alle notifiche nelle impostazioni per contare la musica a schermo spento." }
en = { title = "Background listening isn't being measured.", action = "Allow notification access in settings so music with the screen off is counted." }
```

Continue in the same shape for every remaining code. Write all four languages for
each — the tests from Step 2 fail on a missing locale, an empty string, an ß in
German, or a word that implies secrecy, so a half-written entry cannot slip past. Write the entry for each of these, in this order, with the weight and reach given:

| Code | weight | reach | what it means, for the copy |
|---|---|---|---|
| SZ-E101 | urgent | web, ios, android | email or password wrong |
| SZ-E102 | neutral | web, ios, android | not signed in; sign in again |
| SZ-E103 | neutral | web, ios | the session ran out; sign in again |
| SZ-E104 | neutral | web, ios | this server does not accept new accounts |
| SZ-E105 | urgent | web, ios | that email already has an account |
| SZ-E106 | urgent | ios, android | the parent password did not match, so child mode stays on |
| SZ-E201 | neutral | web, ios, android | that is not here |
| SZ-E202 | neutral | web, ios | that child is not on this server any more |
| SZ-E203 | urgent | ios, android | this phone is not enrolled; enrol it again |
| SZ-E204 | urgent | ios, android | that pairing code is not right |
| SZ-E205 | neutral | ios, android | that pairing code has expired; make a new one |
| SZ-E301 | urgent | web, ios, android | the server could not use what was sent |
| SZ-E302 | urgent | ios, android | too much at once; it will be sent in smaller pieces |
| SZ-E303 | urgent | ios, android | the app is older than the server expects; update it |
| SZ-E304 | neutral | web, ios, android | too many tries; wait a moment |
| SZ-E501 | neutral | web, ios, android | no connection |
| SZ-E502 | neutral | web, ios, android | the server took too long |
| SZ-E503 | urgent | web, ios, android | the secure connection failed |
| SZ-E504 | urgent | web, ios, android | the answer was not from the server it expected |
| SZ-E505 | urgent | web, ios, android | the server could not be reached at that address |
| SZ-E506 | urgent | ios, android | no server address is set yet |
| SZ-E601 | urgent | android | usage access was switched off, so nothing is being counted |
| SZ-E602 | neutral | android | notifications are off, so the ongoing notice cannot show |
| SZ-E603 | neutral | android | notification access is off, so background listening cannot be measured |
| SZ-E604 | urgent | ios | Screen Time access was declined, so nothing is being counted |
| SZ-E605 | neutral | ios | background refresh is off, so numbers arrive late |
| SZ-E701 | urgent | ios | the saved sign-in could not be read |
| SZ-E702 | urgent | ios | the sign-in could not be saved |
| SZ-E703 | urgent | web, ios, android | saved data on this device could not be read |
| SZ-E704 | urgent | ios, android | an hour could not be written to the queue |
| SZ-E705 | urgent | ios, android | the time zone is not one this app knows |
| SZ-E706 | urgent | ios, android | the data could not be read |
| SZ-E707 | urgent | web | something unexpected went wrong in the app |
| SZ-E901 | urgent | web, ios, android | the server hit a problem on its side |
| SZ-E902 | urgent | web, ios, android | the server cannot reach its database |

Copy rules while writing these: plain language, short sentences, say what happened and then what to do. du-form in German. No ß. Never imply secrecy. `SZ-E601` and `SZ-E604` are the two where the action matters most — a parent whose counting has silently stopped needs to be told exactly which switch to turn back on.

- [ ] **Step 6: Run the tests**

Run: `cargo test -p copygen`
Expected: PASS, 6 tests.

- [ ] **Step 7: Prove the tests are not vacuous**

Delete the `it = { ... }` line from `[SZ-E101]`.
Run: `cargo test -p copygen`
Expected: `every_entry_has_all_four_locales` FAILS with "SZ-E101 is missing it". Restore it.

Change `[SZ-E101]` to `[SZ-E999]`.
Expected: `every_code_has_an_entry` FAILS ("SZ-E101 has no entry") and `every_entry_names_a_real_code` FAILS ("SZ-E999 is not a known code"). Restore.

- [ ] **Step 8: Commit**

```bash
git add copy/errors.toml crates/copygen Cargo.toml
git commit -F - <<'MSG'
feat: one copy source for every error, in four languages

refs: SZ-ERRORS
MSG
```

---

### Task 3: Generate the per-surface dictionaries

**Files:**
- Modify: `crates/copygen/src/main.rs`
- Create: `crates/copygen/src/emit.rs`
- Modify: `justfile`
- Generated (committed): `web/src/i18n/errors.ts`, `ios/Sources/Resources/{de,fr,it,en}.lproj/ErrorCopy.strings`, `android/app/src/main/res/{values,values-de,values-fr,values-it}/error_copy.xml`
- Test: `crates/copygen/src/emit.rs` (inline tests)

**Interfaces:**
- Consumes: `catalog::{Catalog, Entry, Message, Surface, Weight}` from Task 2.
- Produces: `emit::web(&Catalog) -> String`, `emit::ios(&Catalog, locale: &str) -> String`, `emit::android(&Catalog, locale: &str) -> String`, and the `just gen-copy` / `just gen-copy-check` recipes. The web plan imports `errorCopy` and `ErrorCopyCode` from `web/src/i18n/errors.ts`; the iOS plan reads `ErrorCopy.strings`; the Android plan reads `R.string.error_SZ_E501_title`.

- [ ] **Step 1: Write the failing test**

In `crates/copygen/src/emit.rs`:

```rust
#[cfg(test)]
mod tests {
    use super::*;

    fn tiny() -> Catalog {
        toml::from_str(
            r#"
            [SZ-E501]
            weight = "neutral"
            reach = ["web", "ios", "android"]
            de = { title = "Keine Verbindung.", action = "Prüf dein WLAN." }
            fr = { title = "Pas de connexion.", action = "Vérifie ton Wi-Fi." }
            it = { title = "Nessuna connessione.", action = "Controlla il Wi-Fi." }
            en = { title = "No connection.", action = "Check your Wi-Fi." }

            [SZ-E603]
            weight = "neutral"
            reach = ["android"]
            de = { title = "Kein Zugriff.", action = "Schalte ihn ein." }
            fr = { title = "Pas d'accès.", action = "Active-le." }
            it = { title = "Nessun accesso.", action = "Attivalo." }
            en = { title = "No access.", action = "Switch it on." }
            "#,
        )
        .unwrap()
    }

    #[test]
    fn web_gets_only_the_codes_it_can_emit() {
        let out = web(&tiny());
        assert!(out.contains("'SZ-E501'"), "{out}");
        assert!(
            !out.contains("SZ-E603"),
            "web cannot emit an Android notification-access failure: {out}"
        );
    }

    #[test]
    fn web_carries_all_four_locales_and_the_weight() {
        let out = web(&tiny());
        for locale in ["de:", "fr:", "it:", "en:"] {
            assert!(out.contains(locale), "missing {locale} in {out}");
        }
        assert!(out.contains("weight: 'neutral'"), "{out}");
    }

    #[test]
    fn ios_escapes_quotes_and_keys_by_code() {
        let out = ios(&tiny(), "en");
        assert!(out.contains(r#""error.SZ-E501.title" = "No connection.";"#), "{out}");
        assert!(out.contains(r#""error.SZ-E501.action" = "Check your Wi-Fi.";"#), "{out}");
        assert!(!out.contains("SZ-E603"), "iOS cannot emit SZ-E603: {out}");
    }

    /// An unescaped apostrophe in French is a build error in Android resources,
    /// and an unescaped & is a parse error. Both appear in real copy.
    #[test]
    fn android_escapes_what_the_resource_parser_would_choke_on() {
        let out = android(&tiny(), "fr");
        assert!(out.contains(r"Pas d\'accès"), "{out}");
        assert!(out.contains(r#"name="error_SZ_E603_title""#), "{out}");
        assert!(!out.contains("SZ-E501\""), "codes use underscores in resource names: {out}");
    }

    #[test]
    fn generated_files_say_they_are_generated() {
        assert!(web(&tiny()).starts_with("// Generated by `just gen-copy`"));
        assert!(ios(&tiny(), "de").starts_with("/* Generated by `just gen-copy`"));
        assert!(android(&tiny(), "de").contains("<!-- Generated by `just gen-copy`"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cargo test -p copygen emit`
Expected: FAIL to compile — `web`, `ios`, `android` not found.

- [ ] **Step 3: Write the emitters**

Above the tests in `crates/copygen/src/emit.rs`:

```rust
use crate::catalog::{Catalog, Surface, Weight};

const LOCALES: [&str; 4] = ["de", "fr", "it", "en"];

fn weight_str(weight: Weight) -> &'static str {
    match weight {
        Weight::Urgent => "urgent",
        Weight::Neutral => "neutral",
    }
}

/// One TypeScript module holding every locale, because the dashboard already
/// ships all four dictionaries and switches between them at runtime.
pub fn web(catalog: &Catalog) -> String {
    let mut out = String::from(
        "// Generated by `just gen-copy` from copy/errors.toml. Do not edit.\n\n\
         export type ErrorWeight = 'urgent' | 'neutral'\n\n\
         export type ErrorCopyEntry = {\n  weight: ErrorWeight\n  \
         de: { title: string; action: string }\n  \
         fr: { title: string; action: string }\n  \
         it: { title: string; action: string }\n  \
         en: { title: string; action: string }\n}\n\n\
         export const errorCopy = {\n",
    );
    for (code, entry) in &catalog.entries {
        if !entry.reach.contains(&Surface::Web) {
            continue;
        }
        out.push_str(&format!("  '{code}': {{\n    weight: '{}',\n", weight_str(entry.weight)));
        for locale in LOCALES {
            let copy = &entry.locales[locale];
            out.push_str(&format!(
                "    {locale}: {{ title: {}, action: {} }},\n",
                ts_string(&copy.title),
                ts_string(&copy.action)
            ));
        }
        out.push_str("  },\n");
    }
    out.push_str("} as const satisfies Record<string, ErrorCopyEntry>\n\n");
    out.push_str("export type ErrorCopyCode = keyof typeof errorCopy\n");
    out
}

/// One .strings per lproj, keyed `error.<code>.title` / `.action`.
pub fn ios(catalog: &Catalog, locale: &str) -> String {
    let mut out = String::from("/* Generated by `just gen-copy` from copy/errors.toml. Do not edit. */\n\n");
    for (code, entry) in &catalog.entries {
        if !entry.reach.contains(&Surface::Ios) {
            continue;
        }
        let copy = &entry.locales[locale];
        out.push_str(&format!(
            "\"error.{code}.title\" = \"{}\";\n\"error.{code}.action\" = \"{}\";\n",
            strings_escape(&copy.title),
            strings_escape(&copy.action)
        ));
    }
    out
}

/// Android resource names cannot contain a hyphen, so SZ-E501 becomes
/// error_SZ_E501_title.
pub fn android(catalog: &Catalog, locale: &str) -> String {
    let mut out = String::from(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n\
         <!-- Generated by `just gen-copy` from copy/errors.toml. Do not edit. -->\n\
         <resources>\n",
    );
    for (code, entry) in &catalog.entries {
        if !entry.reach.contains(&Surface::Android) {
            continue;
        }
        let name = code.replace('-', "_");
        let copy = &entry.locales[locale];
        out.push_str(&format!(
            "    <string name=\"error_{name}_title\">{}</string>\n    \
             <string name=\"error_{name}_action\">{}</string>\n",
            android_escape(&copy.title),
            android_escape(&copy.action)
        ));
    }
    out.push_str("</resources>\n");
    out
}

fn ts_string(value: &str) -> String {
    format!("'{}'", value.replace('\\', "\\\\").replace('\'', "\\'"))
}

fn strings_escape(value: &str) -> String {
    value.replace('\\', "\\\\").replace('"', "\\\"")
}

/// `&` and `<` break the XML parse; a bare apostrophe breaks the Android
/// resource compiler specifically, and French copy is full of them.
fn android_escape(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('\'', "\\'")
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cargo test -p copygen emit`
Expected: PASS, 5 tests.

- [ ] **Step 5: Write the generator binary**

`crates/copygen/src/main.rs`:

```rust
mod catalog;
mod emit;

use catalog::Catalog;

/// Writes every generated dictionary. `just gen-copy-check` then diffs them
/// against git, so a copy change that is not regenerated and committed fails
/// the gate — the same contract `schema.d.ts` already lives by.
fn main() {
    let root = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    let catalog = Catalog::load(root.join("copy/errors.toml")).unwrap_or_else(|e| {
        eprintln!("{e}");
        std::process::exit(1);
    });

    write(&root.join("web/src/i18n/errors.ts"), &emit::web(&catalog));

    for locale in ["de", "fr", "it", "en"] {
        write(
            &root.join(format!("ios/Sources/Resources/{locale}.lproj/ErrorCopy.strings")),
            &emit::ios(&catalog, locale),
        );
        // Android's default resource dir carries English; the others are
        // qualified. Matches the existing values/ + values-de/fr/it layout.
        let dir = if locale == "en" { "values".to_string() } else { format!("values-{locale}") };
        write(
            &root.join(format!("android/app/src/main/res/{dir}/error_copy.xml")),
            &emit::android(&catalog, locale),
        );
    }
}

fn write(path: &std::path::Path, contents: &str) {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).expect("create output directory");
    }
    std::fs::write(path, contents).unwrap_or_else(|e| panic!("{}: {e}", path.display()));
}
```

- [ ] **Step 6: Add the just recipes**

In `justfile`, next to `gen` / `gen-check`:

```make
gen-copy:
    cargo run --quiet -p copygen

gen-copy-check: gen-copy
    git diff --exit-code web/src/i18n/errors.ts ios/Sources/Resources android/app/src/main/res
```

Change `web-check` to depend on both:

```make
web-check: gen-check gen-copy-check
    cd web && pnpm tsc -b --noEmit && pnpm vitest run
```

- [ ] **Step 7: Generate and inspect the output**

Run: `just gen-copy`
Then read `web/src/i18n/errors.ts` and one of `android/app/src/main/res/values-fr/error_copy.xml` end to end. Look at the French apostrophes specifically. Generated files still ship to parents; a mangled string is a mangled string.

- [ ] **Step 8: Verify the check recipe actually catches drift**

Run: `just gen-copy && git add -A && just gen-copy-check`
Expected: PASS (no diff).

Now change one German title in `copy/errors.toml` and run `just gen-copy-check` again.
Expected: FAIL with a diff on `web/src/i18n/errors.ts`. Revert the copy change and regenerate.

- [ ] **Step 9: Commit**

```bash
git add crates/copygen justfile web/src/i18n/errors.ts ios/Sources/Resources android/app/src/main/res
git commit -F - <<'MSG'
feat: generate the error dictionaries for all three clients

refs: SZ-ERRORS
MSG
```

---

### Task 4: `ApiError` gains a code, and the problem body becomes a type

**Files:**
- Modify: `crates/server/src/error.rs`, `crates/server/src/openapi.rs`, `crates/server/Cargo.toml`
- Test: `crates/server/tests/problem.rs` (create)

**Interfaces:**
- Consumes: `ErrorCode` from Task 1.
- Produces: `schirmziit_server::error::{ApiError, Problem}`, `ApiError::code(&self) -> ErrorCode`, and a `Problem` serialised with the fields `type`, `title`, `status`, `detail`, `code`, `ref`. Task 5 fills `ref`; Task 6 constructs `Problem` for foreign errors.

- [ ] **Step 1: Write the failing test**

Create `crates/server/tests/problem.rs`:

```rust
//! The shape of every error the API returns.
//!
//! A parent reads the code off a screenshot and a self-hoster greps the
//! reference out of the log. Both of those only work if every error response
//! carries both — including the ones axum produced without asking us.

mod helpers;
use helpers::TestApp;
use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use sqlx::PgPool;

#[sqlx::test]
async fn an_unauthenticated_request_carries_its_code(pool: PgPool) {
    let app = TestApp::new(pool);
    let response = app.get("/v1/children").await;

    assert_eq!(response.status, StatusCode::UNAUTHORIZED);
    assert_eq!(response.json["code"], "SZ-E102");
    assert_eq!(response.json["status"], 401);
    assert!(
        response.json["ref"].as_str().is_some_and(|r| r.len() == 6),
        "every problem carries a 6-character reference: {}",
        response.json
    );
}

#[sqlx::test]
async fn bad_credentials_carry_their_code(pool: PgPool) {
    let app = TestApp::new(pool);
    let response = app
        .post_json(
            "/v1/auth/login",
            serde_json::json!({ "email": "nobody@example.com", "password": "wrong" }),
        )
        .await;

    assert_eq!(response.status, StatusCode::UNAUTHORIZED);
    assert_eq!(response.json["code"], "SZ-E101");
}

#[sqlx::test]
async fn the_problem_content_type_is_unchanged(pool: PgPool) {
    // `type` and `title` are a stable contract older clients may match on.
    let app = TestApp::new(pool);
    let response = app.get("/v1/children").await;
    assert_eq!(
        response.json["type"],
        "https://schirmziit.ch/problems/unauthenticated"
    );
    assert_eq!(response.json["title"], "unauthenticated");
}
```

The `Response` helper in `crates/server/tests/helpers/mod.rs` already exposes `status` and `json`, so no helper change is needed.

- [ ] **Step 2: Run test to verify it fails**

Run: `cargo test -p schirmziit-server --test problem`
Expected: FAIL — `response.json["code"]` is `Null`, not `"SZ-E102"`.

- [ ] **Step 3: Add the code mapping and the `Problem` type**

In `crates/server/src/error.rs`, replace the `parts` method and `IntoResponse` impl:

```rust
use schirmziit_core::codes::ErrorCode;

impl ApiError {
    fn parts(&self) -> (StatusCode, &'static str) {
        // unchanged
    }

    /// The catalog code for this failure. The `type` slug above stays as it is
    /// — it is an older contract and clients may already match on it — so a
    /// response carries both.
    pub fn code(&self) -> ErrorCode {
        match self {
            Self::NotFound => ErrorCode::NotFound,
            Self::InvalidCredentials => ErrorCode::InvalidCredentials,
            Self::Unauthenticated => ErrorCode::Unauthenticated,
            Self::RegistrationDisabled => ErrorCode::RegistrationDisabled,
            Self::EmailTaken => ErrorCode::EmailTaken,
            Self::PayloadTooLarge => ErrorCode::PayloadTooLarge,
            Self::UnsupportedSchema(_) => ErrorCode::UnsupportedSchema,
            Self::RateLimited => ErrorCode::RateLimited,
            Self::Validation(_) => ErrorCode::ValidationFailed,
            Self::Database(_) => ErrorCode::Internal,
        }
    }
}

/// The body of every error response.
///
/// `detail` is for the log and the copy-details block a parent can send. It is
/// never rendered as the message a parent reads: it is English, and the app
/// speaks four languages. The client looks the copy up by `code`.
#[derive(Debug, serde::Serialize, serde::Deserialize, utoipa::ToSchema)]
pub struct Problem {
    #[serde(rename = "type")]
    pub r#type: String,
    pub title: String,
    pub status: u16,
    pub detail: String,
    pub code: ErrorCode,
    /// Six hex characters, the head of the request id. Filled in by the
    /// normalise layer, which is the only place with access to the request.
    #[serde(rename = "ref")]
    pub r#ref: String,
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let (status, kind) = self.parts();
        let code = self.code();
        let detail = match self {
            // Never leak database internals to a client.
            Self::Database(_) => "internal error".to_string(),
            other => other.to_string(),
        };
        let body = Problem {
            r#type: format!("https://schirmziit.ch/problems/{kind}"),
            title: kind.to_string(),
            status: status.as_u16(),
            detail,
            code,
            // The layer fills this in; empty here means the layer is missing,
            // which its own test catches.
            r#ref: String::new(),
        };
        let mut response = (status, axum::Json(body)).into_response();
        response.headers_mut().insert(
            axum::http::header::CONTENT_TYPE,
            axum::http::HeaderValue::from_static("application/problem+json"),
        );
        response
    }
}
```

Note the `tracing::error!` call moves out of here in Task 7, where it becomes one log line per request with the reference attached. Leave it in place for now.

`crates/core` must be a `schema`-featured dependency of the server already (it is — `crates/server/Cargo.toml` enables it for `wire`); confirm `ErrorCode` derives `ToSchema` under that feature.

- [ ] **Step 4: Register the schemas**

In `crates/server/src/openapi.rs`, add to `components(schemas(...))`:

```rust
        crate::error::Problem,
        schirmziit_core::codes::ErrorCode,
```

- [ ] **Step 5: Run the tests**

Run: `cargo test -p schirmziit-server --test problem`
Expected: the two code assertions PASS; the `ref` length assertion still FAILS (it is empty until Task 5). That is the expected intermediate state — Task 5's first step is to make it pass.

- [ ] **Step 6: Regenerate the API surface**

Run: `just openapi && just gen`
Then check that the code enum reached TypeScript:

```bash
grep -n "SZ-E101" api/openapi.json web/src/api/schema.d.ts
```
Expected: hits in both. If `schema.d.ts` has `code: string` instead of a union, utoipa did not honour the per-variant `serde(rename)` — in that case add `#[schema(example = "SZ-E101")]` and an explicit `#[schema(rename_all = ...)]`-free enum listing, and re-check. Do not proceed with a plain `string`: the union is the whole reason the code goes through openapi rather than a hand-written file.

- [ ] **Step 7: Add a contract test for the generated enum**

In `crates/server/tests/contract.rs`:

```rust
/// The dashboard's generated union comes from this enum. If utoipa ever stops
/// emitting the renamed strings, web silently falls back to `string` and the
/// copy lookup loses its compile-time check.
#[test]
fn the_error_code_enum_reaches_the_document() {
    let doc = serde_json::to_value(schirmziit_server::openapi::ApiDoc::openapi()).unwrap();
    let values = doc["components"]["schemas"]["ErrorCode"]["enum"]
        .as_array()
        .expect("ErrorCode is an enum schema");
    assert!(values.iter().any(|v| v == "SZ-E101"), "{values:?}");
    assert!(values.iter().any(|v| v == "SZ-E901"), "{values:?}");
}
```

- [ ] **Step 8: Commit**

```bash
git add crates/server/src/error.rs crates/server/src/openapi.rs crates/server/tests/problem.rs crates/server/tests/contract.rs api/openapi.json web/src/api/schema.d.ts
git commit -F - <<'MSG'
feat: every API error carries a catalog code

refs: SZ-ERRORS
MSG
```

---

### Task 5: Request id, and the reference in the body

**Files:**
- Create: `crates/server/src/request_id.rs`
- Modify: `crates/server/src/lib.rs`, `crates/server/Cargo.toml`
- Test: `crates/server/tests/problem.rs`, `crates/server/tests/cors.rs`

**Interfaces:**
- Consumes: `Problem` from Task 4.
- Produces: `request_id::{RequestRef, layer}` — `RequestRef(pub String)` is inserted into request extensions and holds the full uuid; `RequestRef::short(&self) -> String` is its first six characters. Task 6's middleware reads it; Task 7's log line prints both.

- [ ] **Step 1: Write the failing test**

Add to `crates/server/tests/problem.rs`:

```rust
#[sqlx::test]
async fn every_response_carries_a_request_id_header(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let response = app.get_raw("/v1/me").await;

    assert!(response.status().is_success());
    let header = response
        .headers()
        .get("x-request-id")
        .expect("x-request-id on a successful response")
        .to_str()
        .unwrap()
        .to_string();
    // A successful-but-slow request has to be traceable too, which is why this
    // is not limited to errors.
    assert_eq!(header.len(), 36, "a uuid, not {header}");
}

#[sqlx::test]
async fn the_body_reference_is_the_head_of_the_request_id(pool: PgPool) {
    let app = TestApp::new(pool);
    let (status, json, header) = app.get_with_headers("/v1/children").await;

    assert_eq!(status, StatusCode::UNAUTHORIZED);
    let request_id = header.get("x-request-id").unwrap().to_str().unwrap();
    let short = json["ref"].as_str().unwrap();
    assert_eq!(
        short,
        &request_id[..6],
        "grepping the on-screen reference must find the log line"
    );
}
```

Add these two helpers to `crates/server/tests/helpers/mod.rs`, next to the existing `get`:

```rust
    /// The whole response, for tests that care about headers.
    pub async fn get_raw(&self, uri: &str) -> axum::http::Response<Body> {
        let mut builder = Request::builder().method("GET").uri(uri);
        if let Some(cookie) = &self.cookie {
            builder = builder.header(header::COOKIE, cookie);
        }
        self.router
            .clone()
            .oneshot(builder.body(Body::empty()).unwrap())
            .await
            .unwrap()
    }

    pub async fn get_with_headers(
        &self,
        uri: &str,
    ) -> (StatusCode, serde_json::Value, axum::http::HeaderMap) {
        let response = self.get_raw(uri).await;
        let status = response.status();
        let headers = response.headers().clone();
        let bytes = response.into_body().collect().await.unwrap().to_bytes();
        let json = serde_json::from_slice(&bytes).unwrap_or(serde_json::Value::Null);
        (status, json, headers)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cargo test -p schirmziit-server --test problem`
Expected: FAIL — "x-request-id on a successful response" panics; the reference test fails because `ref` is still empty.

- [ ] **Step 3: Add the dependency**

In `crates/server/Cargo.toml`:

```toml
tower-http = { version = "0.7", features = ["cors", "trace", "request-id"] }
```

- [ ] **Step 4: Write the layer**

`crates/server/src/request_id.rs`:

```rust
//! One id per request, so an error a parent photographs can be found in the log.
//!
//! The header carries the whole uuid; the on-screen reference is its first six
//! characters. Six is enough to grep a family's own server — this is a
//! search key inside one log, not a globally unique identifier — and it is
//! short enough to read off a screenshot without transcription errors.

use axum::http::{HeaderName, Request};
use tower_http::request_id::{MakeRequestId, RequestId};

pub const HEADER: HeaderName = HeaderName::from_static("x-request-id");

/// Inserted into the request extensions so handlers and layers can reach it.
#[derive(Debug, Clone)]
pub struct RequestRef(pub String);

impl RequestRef {
    pub fn short(&self) -> String {
        self.0.chars().take(6).collect()
    }
}

#[derive(Clone, Default)]
pub struct MakeUuid;

impl MakeRequestId for MakeUuid {
    fn make_request_id<B>(&mut self, _request: &Request<B>) -> Option<RequestId> {
        let id = uuid::Uuid::new_v4().to_string();
        id.parse().ok().map(RequestId::new)
    }
}
```

The server does not trust an inbound `x-request-id`: `SetRequestIdLayer` only fills the header when it is absent, and Traefik does not set one for this service. If that ever changes, a client-supplied id would end up in the log — which is why the log line in Task 7 prints the id it generated, not the header it received.

- [ ] **Step 5: Wire it into the router**

In `crates/server/src/lib.rs`, add `pub mod request_id;` and wrap the finished router. The layers go outside everything, including the CORS layer, so even a rejected preflight is traceable:

```rust
use tower_http::request_id::{PropagateRequestIdLayer, SetRequestIdLayer};

    family_routes
        .merge(waitlist_routes.layer(waitlist_cors_layer()))
        .layer(PropagateRequestIdLayer::new(request_id::HEADER))
        .layer(axum::middleware::from_fn(insert_request_ref))
        .layer(SetRequestIdLayer::new(
            request_id::HEADER,
            request_id::MakeUuid,
        ))
        .with_state(state)
```

And the small middleware that copies the generated header into a typed extension:

```rust
/// `SetRequestIdLayer` puts the id in the headers; everything downstream wants
/// it as a value, not a header lookup.
async fn insert_request_ref(
    mut request: axum::extract::Request,
    next: axum::middleware::Next,
) -> axum::response::Response {
    if let Some(id) = request
        .headers()
        .get(request_id::HEADER)
        .and_then(|v| v.to_str().ok())
        .map(|v| request_id::RequestRef(v.to_string()))
    {
        request.extensions_mut().insert(id);
    }
    next.run(request).await
}
```

Layer order matters and is easy to get backwards: `SetRequestIdLayer` must be **outermost** so the header exists before anything reads it, and `PropagateRequestIdLayer` must be innermost of the three so it copies the id onto the response after the handler has run.

- [ ] **Step 6: Expose the header to the browser**

In `crates/server/src/lib.rs`'s `cors_layer`, add to the builder:

```rust
        .expose_headers([request_id::HEADER])
```

Without this the dashboard can read the reference from an error body but not from a successful response, so a slow-but-working request is untraceable from the browser.

- [ ] **Step 7: Add the CORS test**

In `crates/server/tests/cors.rs`:

```rust
#[sqlx::test]
async fn the_request_id_is_exposed_to_the_dashboard(pool: PgPool) {
    let response = app(state(pool, &[DASHBOARD]))
        .oneshot(get("/v1/me", DASHBOARD))
        .await
        .unwrap();

    let exposed = response
        .headers()
        .get(header::ACCESS_CONTROL_EXPOSE_HEADERS)
        .expect("the dashboard cannot read a header it is not granted")
        .to_str()
        .unwrap();
    assert!(exposed.contains("x-request-id"), "{exposed}");
}
```

- [ ] **Step 8: Run the tests**

Run: `cargo test -p schirmziit-server --test problem --test cors`
Expected: the header tests PASS. `the_body_reference_is_the_head_of_the_request_id` and the `ref` length assertion still FAIL — nothing fills the body's `ref` yet. Task 6 does.

- [ ] **Step 9: Commit**

```bash
git add crates/server/src/request_id.rs crates/server/src/lib.rs crates/server/Cargo.toml crates/server/tests/helpers/mod.rs crates/server/tests/cors.rs crates/server/tests/problem.rs
git commit -F - <<'MSG'
feat: one request id per request, exposed to the dashboard

refs: SZ-ERRORS
MSG
```

---

### Task 6: Normalise every error response

**Files:**
- Create: `crates/server/src/normalize.rs`
- Modify: `crates/server/src/lib.rs`
- Test: `crates/server/tests/problem.rs`

**Interfaces:**
- Consumes: `RequestRef` (Task 5), `Problem` and `ErrorCode` (Tasks 4 and 1).
- Produces: `normalize::layer()` — an `axum::middleware::from_fn` layer. Nothing later consumes it directly; it is the last word on what an error response looks like.

- [ ] **Step 1: Write the failing test**

Add to `crates/server/tests/problem.rs`:

```rust
#[sqlx::test]
async fn an_unknown_api_path_is_a_problem_body(pool: PgPool) {
    // The router's fallback is the SPA handler, which returns a bare 404 for
    // /v1 paths — no body, no code, nothing to report.
    let app = TestApp::new(pool);
    let (status, json, _) = app.get_with_headers("/v1/nope").await;

    assert_eq!(status, StatusCode::NOT_FOUND);
    assert_eq!(json["code"], "SZ-E201");
    assert_eq!(json["ref"].as_str().unwrap().len(), 6);
}

#[sqlx::test]
async fn a_malformed_body_is_a_problem_body(pool: PgPool) {
    let app = TestApp::new(pool);
    let response = app
        .send_raw(
            Request::builder()
                .method("POST")
                .uri("/v1/auth/login")
                .header(header::CONTENT_TYPE, "application/json")
                .body(Body::from("{not json"))
                .unwrap(),
        )
        .await;

    assert_eq!(response.status, StatusCode::BAD_REQUEST);
    assert_eq!(response.json["code"], "SZ-E301");
}

#[sqlx::test]
async fn the_wrong_method_is_a_problem_body(pool: PgPool) {
    let app = TestApp::new(pool);
    let response = app
        .send_raw(
            Request::builder()
                .method("DELETE")
                .uri("/v1/me")
                .body(Body::empty())
                .unwrap(),
        )
        .await;

    assert_eq!(response.status, StatusCode::METHOD_NOT_ALLOWED);
    assert_eq!(response.json["code"], "SZ-E301");
}

#[sqlx::test]
async fn a_missed_page_is_still_the_dashboard_not_a_problem_body(pool: PgPool) {
    // A browser asking for a deep link must not be handed application/json.
    // Only API paths are normalised.
    let app = TestApp::new(pool);
    let response = app.get_raw("/children/some-id").await;

    let content_type = response
        .headers()
        .get(header::CONTENT_TYPE)
        .map(|v| v.to_str().unwrap().to_string())
        .unwrap_or_default();
    assert!(
        !content_type.starts_with("application/problem+json"),
        "page requests must not be normalised: {content_type}"
    );
}
```

Add a `send_raw` helper to `crates/server/tests/helpers/mod.rs` that takes a full `Request<Body>` and returns the existing `Response` struct (status + parsed json), mirroring `post_json`'s body handling.

- [ ] **Step 2: Run test to verify it fails**

Run: `cargo test -p schirmziit-server --test problem`
Expected: FAIL — `json["code"]` is `Null` for all three normalisation tests, because those responses have no body at all.

- [ ] **Step 3: Write the middleware**

`crates/server/src/normalize.rs`:

```rust
//! The last word on what an error response looks like.
//!
//! Two jobs. First, fill in the reference: `ApiError::into_response` builds the
//! body but cannot reach the request, and threading an extractor through every
//! handler to carry one string would be a tax on every future route.
//!
//! Second, and this is the one that makes "every error carries a code" true
//! rather than aspirational: axum produces error responses nobody wrote. The
//! SPA fallback's 404 on an unknown `/v1` path, a 405, a body over the limit, a
//! JSON body that will not deserialise — all of those left the server as a bare
//! status with no body. A client could report nothing about them, which is
//! exactly when a parent most needs something to report.

use axum::body::Body;
use axum::extract::Request;
use axum::http::{StatusCode, header};
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};
use http_body_util::BodyExt;
use schirmziit_core::codes::ErrorCode;

use crate::error::Problem;
use crate::request_id::RequestRef;

/// Only API paths. A browser following a deep link gets the dashboard, and
/// handing it `application/problem+json` would turn a working page into a
/// download prompt.
fn is_api_path(path: &str) -> bool {
    path.starts_with("/v1/") || path == "/healthz"
}

fn code_for(status: StatusCode) -> ErrorCode {
    match status {
        StatusCode::NOT_FOUND => ErrorCode::NotFound,
        StatusCode::UNAUTHORIZED => ErrorCode::Unauthenticated,
        StatusCode::PAYLOAD_TOO_LARGE => ErrorCode::PayloadTooLarge,
        StatusCode::TOO_MANY_REQUESTS => ErrorCode::RateLimited,
        s if s.is_client_error() => ErrorCode::ValidationFailed,
        _ => ErrorCode::Internal,
    }
}

pub async fn normalize(request: Request, next: Next) -> Response {
    let short = request
        .extensions()
        .get::<RequestRef>()
        .map(RequestRef::short)
        .unwrap_or_default();
    let path = request.uri().path().to_string();

    let response = next.run(request).await;
    let status = response.status();
    if status.is_success() || status.is_redirection() || !is_api_path(&path) {
        return response;
    }

    let is_problem = response
        .headers()
        .get(header::CONTENT_TYPE)
        .and_then(|v| v.to_str().ok())
        .is_some_and(|v| v.starts_with("application/problem+json"));

    let (parts, body) = response.into_parts();
    let bytes = match body.collect().await {
        Ok(collected) => collected.to_bytes(),
        // A body that cannot even be read is itself an internal error, and
        // returning the unreadable thing helps nobody.
        Err(_) => Default::default(),
    };

    let problem = if is_problem {
        match serde_json::from_slice::<Problem>(&bytes) {
            Ok(mut problem) => {
                problem.r#ref = short;
                problem
            }
            Err(_) => fallback(status, short),
        }
    } else {
        fallback(status, short)
    };

    let mut rebuilt = (parts.status, axum::Json(problem)).into_response();
    // Keep whatever the inner response set — a WWW-Authenticate, a CORS grant —
    // and only overwrite what the new body dictates.
    for (name, value) in parts.headers.iter() {
        if name != header::CONTENT_TYPE && name != header::CONTENT_LENGTH {
            rebuilt.headers_mut().insert(name.clone(), value.clone());
        }
    }
    rebuilt.headers_mut().insert(
        header::CONTENT_TYPE,
        axum::http::HeaderValue::from_static("application/problem+json"),
    );
    rebuilt
}

fn fallback(status: StatusCode, short: String) -> Problem {
    let code = code_for(status);
    Problem {
        r#type: format!("https://schirmziit.ch/problems/{}", code.as_str()),
        title: status
            .canonical_reason()
            .unwrap_or("error")
            .to_lowercase()
            .replace(' ', "-"),
        status: status.as_u16(),
        // Never the upstream body: a rejection message from a deserialiser can
        // quote the payload, and the payload is a family's data.
        detail: "request failed".to_string(),
        code,
        r#ref: short,
    }
}
```

There is no `layer()` constructor: the wiring uses `axum::middleware::from_fn(normalize)` at the call site, which is how `insert_request_ref` is already mounted.

- [ ] **Step 4: Wire it in**

In `crates/server/src/lib.rs`, add `pub mod normalize;` and put it between the request-ref middleware and the propagate layer, so it runs after handlers and still sees the extension:

```rust
    family_routes
        .merge(waitlist_routes.layer(waitlist_cors_layer()))
        .layer(PropagateRequestIdLayer::new(request_id::HEADER))
        .layer(axum::middleware::from_fn(normalize::normalize))
        .layer(axum::middleware::from_fn(insert_request_ref))
        .layer(SetRequestIdLayer::new(
            request_id::HEADER,
            request_id::MakeUuid,
        ))
        .with_state(state)
```

- [ ] **Step 5: Run the tests**

Run: `cargo test -p schirmziit-server --test problem`
Expected: PASS, all of them — including `the_body_reference_is_the_head_of_the_request_id` and the `ref` length assertion from Tasks 4 and 5.

- [ ] **Step 6: Prove the tests are not vacuous**

Change `is_api_path` to `fn is_api_path(_: &str) -> bool { true }`.
Expected: `a_missed_page_is_still_the_dashboard_not_a_problem_body` FAILS. Revert.

Change `problem.r#ref = short;` to `problem.r#ref = String::new();`.
Expected: `the_body_reference_is_the_head_of_the_request_id` FAILS. Revert.

- [ ] **Step 7: Run the whole server suite**

Run: `cargo test -p schirmziit-server`
Expected: PASS. Pay attention to `static_files.rs` and `serve.rs` — they assert on 404s and are the tests most likely to notice a normalisation that reached too far. If one fails, the middleware is normalising a page request; fix `is_api_path`, not the test.

- [ ] **Step 8: Commit**

```bash
git add crates/server/src/normalize.rs crates/server/src/lib.rs crates/server/tests/problem.rs crates/server/tests/helpers/mod.rs
git commit -F - <<'MSG'
fix: give every error response a code and a reference

refs: SZ-ERRORS
MSG
```

---

### Task 7: One log line per request

**Files:**
- Modify: `crates/server/src/lib.rs`, `crates/server/src/main.rs`, `crates/server/src/error.rs`
- Test: `crates/server/tests/logging.rs` (create)

**Interfaces:**
- Consumes: `RequestRef` (Task 5), `ApiError::code()` (Task 4).
- Produces: nothing other tasks call. This is the half of the feature that makes the reference worth printing.

- [ ] **Step 1: Write the failing test**

Create `crates/server/tests/logging.rs`:

```rust
//! What the server writes down when something fails.
//!
//! The reference on a parent's screenshot is only useful if `grep` finds it,
//! and a log line is only safe if it holds nothing that identifies a family.

mod helpers;
use helpers::TestApp;
use sqlx::PgPool;
use std::sync::{Arc, Mutex};
use tracing_subscriber::prelude::*;

/// Collects everything written while the guard is alive.
#[derive(Clone, Default)]
struct Captured(Arc<Mutex<Vec<u8>>>);

impl std::io::Write for Captured {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        self.0.lock().unwrap().extend_from_slice(buf);
        Ok(buf.len())
    }
    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

impl Captured {
    fn text(&self) -> String {
        String::from_utf8(self.0.lock().unwrap().clone()).unwrap()
    }
}

fn capture() -> (Captured, tracing::subscriber::DefaultGuard) {
    let sink = Captured::default();
    let writer = sink.clone();
    let subscriber = tracing_subscriber::registry().with(
        tracing_subscriber::fmt::layer()
            .with_writer(move || writer.clone())
            .with_ansi(false),
    );
    let guard = tracing::subscriber::set_default(subscriber);
    (sink, guard)
}

#[sqlx::test]
async fn a_failed_request_logs_its_reference_and_code(pool: PgPool) {
    let (sink, _guard) = capture();
    let app = TestApp::new(pool);
    let (_, json, headers) = app.get_with_headers("/v1/children").await;

    let logged = sink.text();
    let short = json["ref"].as_str().unwrap();
    let full = headers.get("x-request-id").unwrap().to_str().unwrap();

    assert!(logged.contains(short), "grep {short} found nothing in:\n{logged}");
    assert!(logged.contains(full), "the full id belongs in the log too:\n{logged}");
    assert!(logged.contains("SZ-E102"), "the code belongs in the log:\n{logged}");
}

#[sqlx::test]
async fn a_failed_sign_in_never_logs_the_email(pool: PgPool) {
    // A log that records every attempted address is an account-enumeration
    // list, sitting in a family's own journalctl.
    let (sink, _guard) = capture();
    let app = TestApp::new(pool);
    app.post_json(
        "/v1/auth/login",
        serde_json::json!({ "email": "someone@example.com", "password": "wrong" }),
    )
    .await;

    let logged = sink.text();
    assert!(
        !logged.contains("someone@example.com"),
        "the log holds an attempted email:\n{logged}"
    );
    assert!(logged.contains("SZ-E101"), "but it must say what failed:\n{logged}");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cargo test -p schirmziit-server --test logging`
Expected: FAIL — nothing is logged per request, so the reference is absent.

- [ ] **Step 3: Log from the normalise middleware**

The reference, the code and the status are all in one place there already, which beats reconstructing them in a `TraceLayer` callback. In `crates/server/src/normalize.rs`, after `problem` is built and before the response is rebuilt:

```rust
    // One line per failed request, with everything needed to find it again and
    // nothing that identifies the family: no email, no child name, no body.
    let full = request_ref.as_deref().unwrap_or("");
    if status.is_server_error() {
        tracing::error!(
            code = problem.code.as_str(),
            r#ref = %problem.r#ref,
            request_id = %full,
            method = %method,
            path = %path,
            status = status.as_u16(),
            "request failed"
        );
    } else {
        tracing::warn!(
            code = problem.code.as_str(),
            r#ref = %problem.r#ref,
            request_id = %full,
            method = %method,
            path = %path,
            status = status.as_u16(),
            "request failed"
        );
    }
```

Capture `method` and the full id alongside `path` at the top of `normalize`, before `next.run`:

```rust
    let method = request.method().clone();
    let request_ref = request
        .extensions()
        .get::<RequestRef>()
        .map(|r| r.0.clone());
```

and derive `short` from `request_ref` rather than reading the extension twice.

Remove the now-duplicated `tracing::error!` from `ApiError::into_response` in `crates/server/src/error.rs` — it logged only 500s and knew neither the reference nor the path.

- [ ] **Step 4: Run test to verify it passes**

Run: `cargo test -p schirmziit-server --test logging`
Expected: PASS, both tests.

- [ ] **Step 5: Prove the email test is not vacuous**

Temporarily add `email = %"someone@example.com"` as a field on the `warn!` call.
Run: `cargo test -p schirmziit-server --test logging`
Expected: `a_failed_sign_in_never_logs_the_email` FAILS. Remove the field.

- [ ] **Step 6: Log successful requests too**

In `crates/server/src/lib.rs`, add a `TraceLayer` for the successful path, immediately inside the normalise layer:

```rust
        .layer(
            tower_http::trace::TraceLayer::new_for_http().on_response(
                |response: &axum::http::Response<axum::body::Body>,
                 latency: std::time::Duration,
                 _: &tracing::Span| {
                    if response.status().is_success() {
                        tracing::info!(
                            status = response.status().as_u16(),
                            latency_ms = latency.as_millis() as u64,
                            "request"
                        );
                    }
                },
            ),
        )
```

Failures are already covered by the normalise layer's line; this one exists so a slow success is visible. Keep the two from double-logging — the `is_success()` guard is what does it.

- [ ] **Step 7: Check the subscriber configuration**

In `crates/server/src/main.rs`, make sure the subscriber's env filter defaults to something that shows these: `info` for `schirmziit_server`, and `tower_http=warn` so the TraceLayer's own debug spans stay quiet. Read the current `main.rs` first and follow whatever pattern is there rather than replacing it.

- [ ] **Step 8: Run the full gate**

Run: `just rust-check`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add crates/server/src/normalize.rs crates/server/src/lib.rs crates/server/src/main.rs crates/server/src/error.rs crates/server/tests/logging.rs
git commit -F - <<'MSG'
feat: one log line per request, with the reference and the code

refs: SZ-ERRORS
MSG
```

---

### Task 8: Tenancy assertion, catalog docs, and the gate

**Files:**
- Modify: `crates/server/tests/tenancy.rs`, `CLAUDE.md`
- Create: `docs/error-codes.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the documented catalog other plans link to.

- [ ] **Step 1: Write the failing test**

In `crates/server/tests/tenancy.rs`, alongside the existing cross-family assertions:

```rust
/// Another family's child and a child that never existed must be
/// indistinguishable — including in the error code. A separate
/// "not yours" code would leak existence through the catalog itself,
/// which is exactly what the 404-not-403 rule exists to prevent.
#[sqlx::test]
async fn a_cross_family_miss_is_coded_like_any_other_miss(pool: PgPool) {
    let other = TestApp::registered(pool.clone()).await;
    let created = other
        .post_json("/v1/children", serde_json::json!({ "display_name": "Kid" }))
        .await;
    let their_child = created.json["id"].as_str().unwrap().to_string();

    let mine = TestApp::registered(pool).await;
    let theirs = mine.get(&format!("/v1/children/{their_child}/usage?tz=Europe/Zurich")).await;
    let nobodys = mine
        .get(&format!(
            "/v1/children/{}/usage?tz=Europe/Zurich",
            uuid::Uuid::new_v4()
        ))
        .await;

    assert_eq!(theirs.status, nobodys.status);
    assert_eq!(theirs.json["code"], nobodys.json["code"]);
    assert_eq!(theirs.json["code"], "SZ-E201");
}
```

Adjust the URL to whatever shape `tenancy.rs` already uses for a scoped read — read the file first and match its existing calls rather than inventing a path.

- [ ] **Step 2: Run it**

Run: `cargo test -p schirmziit-server --test tenancy`
Expected: PASS (the scoped query already 404s; this pins the code to it). If it fails because the codes differ, that is a real leak — fix the handler, not the test.

- [ ] **Step 3: Prove it is not vacuous**

Temporarily add an `ApiError::NotYours` variant mapped to `ErrorCode::ChildNotFound`, and return it from the cross-family branch.
Expected: the test FAILS on the code comparison. Revert.

- [ ] **Step 4: Write the catalog reference**

Create `docs/error-codes.md` with the full table: code, meaning, which surfaces emit it, weight, and the HTTP status where one applies. State at the top that `copy/errors.toml` is the source of the text and `crates/core/src/codes.rs` the source of the codes, and that this page is written by hand and reviewed when a code is added — it is documentation, not a generated artifact.

- [ ] **Step 5: Update CLAUDE.md**

Two edits:

In **Where things are**, add:

```
| `copy/errors.toml` | The one source for every error message, in all four languages |
```

In the **Feel** section, after the flourish bullet, add:

```
**Error states get entry motion and press feedback, but no flourish.** The
flourish belongs to the data. An interface that animates a failure is enjoying
itself at the parent's expense.
```

- [ ] **Step 6: Run every gate**

Run: `just rust-check`
Run: `just web-check`
Expected: both PASS. `web-check` now also runs `gen-copy-check`, so an uncommitted regeneration fails here.

Android and iOS gates are untouched by this plan — the generated resource files are not referenced by either app yet, which their own plans do. Still run them once:

Run: `just android-check`
Run: `just ios-check`
Expected: PASS. A new unreferenced `error_copy.xml` must not break the Android build; if it does, the escaping in Task 3 is wrong and that is worth knowing now rather than in the Android plan.

- [ ] **Step 7: Verify against the deployed instance**

This is the step that proves the feature rather than the tests. Deploy from `~/Projects/home-network` on `main` (`nu bin/deploy-k8s.nu schirmziit`), remembering the image tag is that repo's HEAD sha, so commit there first.

Then:

```bash
kubectl port-forward deploy/schirmziit 8080:8080
curl -i http://localhost:8080/v1/nope
```

Read the reference out of the JSON body, then:

```bash
kubectl logs deploy/schirmziit | grep <ref>
```

Expected: exactly one line, holding the code, the path and the full request id. If the grep finds nothing, the feature does not work no matter what the tests say.

- [ ] **Step 8: Commit**

```bash
git add crates/server/tests/tenancy.rs docs/error-codes.md CLAUDE.md
git commit -F - <<'MSG'
docs: the error catalog, and the no-flourish rule for error states

refs: SZ-ERRORS
MSG
```

---

## What this plan deliberately leaves out

- Client-side `AppError`, ring buffers and the copy-details payload. Plans 2–4.
- `ErrorPanel` / `ErrorView` / `ErrorCard` and the banner-versus-inline rule. Plans 2–4.
- Consuming the generated dictionaries. This plan generates and commits them; the surfaces import them in their own plans.
- Version plumbing (`VITE_APP_VERSION`, `CFBundleShortVersionString`, `BuildConfig.VERSION_NAME`). It belongs to the surface that renders it.
- The English `problem.detail` currently rendered at `web/src/pages/ChildDetail.tsx:50` and `ios/Sources/Views/ChildrenView.swift:74`. The server-side half is done here — `detail` is documented as log-only — but removing those two reads happens in Plans 2 and 3.
