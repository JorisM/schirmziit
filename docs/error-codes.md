# Error codes

Every error a parent or a child sees carries one of these codes and a six-character
reference, both rendered on screen so a screenshot is enough to identify what failed and
which occurrence it was.

- The codes live in `crates/core/src/codes.rs`. That enum is the source of truth: the
  server maps its `ApiError` onto it, both apps read it through UniFFI, and the dashboard
  gets it as a TypeScript union through `api/openapi.json` → `web/src/api/schema.d.ts`.
- The text lives in `copy/errors.toml`, in all four languages. `just gen-copy` writes the
  dashboard dictionary, the iOS `.strings` and the Android string resources from it, and
  `just gen-copy-check` fails if what is committed does not match.
- This page is written by hand. When a code is added, add a row.

**Numbers are never reused.** A retired code stays retired: an old screenshot must never
come to describe a different failure than it did on the day it was taken. Retired numbers
are listed in `RETIRED` next to the enum, and a test fails if a live code takes one.

**4xx is deliberately empty.** `SZ-E401` beside an HTTP status would read as
"unauthorised" to anyone who has ever seen a 401, and it would mean "internal server
error". Server-side codes are 9xx instead.

## The reference

The server makes a uuid per request and returns it in `x-request-id`. The on-screen
reference is its first six characters, and the log line prints both, so `grep 7f3a9c` on
a self-hosted instance finds the request.

Client-only failures — 5xx, 6xx, 7xx — never reached the server, so their reference is
generated on the device and lives only in that client's local ring buffer. Nothing is
transmitted anywhere: a screenshot, or the copy-details block, is the report.

## The catalog

`weight` is how loudly the error is presented: **urgent** means something broke and needs
attention, **neutral** means expected, self-correcting, or waiting on the user. An offline
phone painting the dashboard red teaches a parent to ignore red.

### 1xx — auth and session

| Code | Meaning | HTTP | Weight | Surfaces |
|---|---|---|---|---|
| SZ-E101 | Email or password wrong | 401 | urgent | web, iOS, Android |
| SZ-E102 | Not signed in | 401 | neutral | web, iOS, Android |
| SZ-E103 | Session ran out | — | neutral | web, iOS |
| SZ-E104 | This server accepts no new accounts | 403 | neutral | web, iOS |
| SZ-E105 | Email already registered | 409 | urgent | web, iOS |
| SZ-E106 | Parent password did not match, child mode stays on | — | urgent | iOS, Android |

### 2xx — scope and existence

| Code | Meaning | HTTP | Weight | Surfaces |
|---|---|---|---|---|
| SZ-E201 | Not here | 404 | neutral | web, iOS, Android |
| SZ-E202 | That child is not on this server | 404 | neutral | web, iOS |
| SZ-E203 | This device is not enrolled | 401 | urgent | iOS, Android |
| SZ-E204 | Pairing code invalid | — | urgent | iOS, Android |
| SZ-E205 | Pairing code expired | — | neutral | iOS, Android |

A cross-family miss returns **SZ-E201**, identical to a genuine miss. A distinct
"not yours" code would leak existence through the catalog itself, which is the whole
reason the API answers 404 and not 403. `crates/server/tests/tenancy.rs` asserts it.

### 3xx — request shape

| Code | Meaning | HTTP | Weight | Surfaces |
|---|---|---|---|---|
| SZ-E301 | The server could not use the request | 422, 400, 405 | urgent | web, iOS, Android |
| SZ-E302 | Payload too large | 413 | urgent | iOS, Android |
| SZ-E303 | App older than the server expects | 400 | urgent | iOS, Android |
| SZ-E304 | Rate limited | 429 | neutral | web, iOS, Android |

### 5xx — transport, client-only

| Code | Meaning | Weight | Surfaces |
|---|---|---|---|
| SZ-E501 | Offline | neutral | web, iOS, Android |
| SZ-E502 | Timed out | neutral | web, iOS, Android |
| SZ-E503 | TLS failed | urgent | web, iOS, Android |
| SZ-E504 | The answer did not come from the server | urgent | web, iOS, Android |
| SZ-E505 | Server unreachable at that address | urgent | web, iOS, Android |
| SZ-E506 | No server address configured | urgent | iOS, Android |

SZ-E504 is the captive-portal case: a guest Wi-Fi login page answering in the server's
place. It must throw rather than be read as "all accepted", or a day of usage is dropped
as delivered.

### 6xx — platform permission, client-only

| Code | Meaning | Weight | Surfaces |
|---|---|---|---|
| SZ-E601 | Usage access revoked — nothing is being counted | urgent | Android |
| SZ-E602 | Notification permission missing | neutral | Android |
| SZ-E603 | Notification access missing — background listening unmeasured | neutral | Android |
| SZ-E604 | Screen Time authorisation denied — nothing is being counted | urgent | iOS |
| SZ-E605 | Background refresh disabled — numbers arrive late | neutral | iOS |

SZ-E603 is the one that makes `background_measured = false` legible: it means "this
device could not observe it", never "nothing played".

### 7xx — local storage and decode, client-only

| Code | Meaning | Weight | Surfaces |
|---|---|---|---|
| SZ-E701 | Saved sign-in could not be read | urgent | iOS |
| SZ-E702 | Sign-in could not be saved | urgent | iOS |
| SZ-E703 | Saved data on this device could not be read | urgent | web, iOS, Android |
| SZ-E704 | An hour could not be queued | urgent | iOS, Android |
| SZ-E705 | Unknown time zone (from `crates/core`) | urgent | iOS, Android |
| SZ-E706 | Malformed JSON (from `crates/core`) | urgent | iOS, Android |
| SZ-E707 | Unexpected client error | urgent | web |

### 9xx — server-side

| Code | Meaning | HTTP | Weight | Surfaces |
|---|---|---|---|---|
| SZ-E901 | Something went wrong on the server | 500 | urgent | web, iOS, Android |
| SZ-E902 | The server cannot reach its database | 500 | urgent | web, iOS, Android |

`detail` in the problem body is English and exists for the log and the copy-details block.
It is never rendered as the message a reader sees — the client looks the copy up by code,
in the reader's own language.
