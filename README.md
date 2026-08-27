# Schirmziit

Free, self-hostable screen-time monitoring for families. A parent sees **how long
and when** a child's phone is used — per app, per hour — so screen time can be
talked about instead of guessed.

What it never collects: messages, searches, photos, keystrokes, URLs, location.
What it never does: block an app or set a limit. The child sees the same numbers
on their own phone; nothing here runs hidden.

Parts: a Rust core and axum API (`crates/`), a React dashboard (`web/`), an
Android child agent (`android/`), one iOS app with a parent and a child role
(`ios/`), and the product site (`site/`). Self-hosters run two containers — the
app and Postgres (`deploy/`).

**What runs where is a table, not a paragraph: [`docs/platform-matrix.md`](docs/platform-matrix.md).**
It is the honest state, gaps included.

## Contributing

Pull requests welcome — bug reports and translation fixes just as much as code.

- Four languages, always: de (Schweizer Hochdeutsch), fr, it, en. A new string
  lands in all four in the same commit.
- Run the gate for what you touched, and all of them before calling it done:
  `just rust-check`, `just web-check`, `just android-check`, `just ios-check`,
  `cd site && pnpm build`.
- Tests first for anything that could lose or expose a day of data — the queue
  and the tenancy rules have tests that exist because a bug once shipped.
- The wire format lives in `crates/core` only. Never hand-roll the ingest JSON in
  Kotlin or Swift.

Blocking, time limits and content filtering are deliberately out of scope. If an
idea implies one, open an issue first — it is a product decision before it is a
patch.

## Licence

[Apache License 2.0](LICENSE). Copy it, run it, fork it, ship it; keep the
notice. The copyright stays with the author, so a paid hosted version later is
possible without changing what you are allowed to do with this code.
