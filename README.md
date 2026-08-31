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

## Run it, self-hosted

Two containers, a reverse proxy you already have, and about five minutes.

```sh
git clone https://github.com/JorisM/schirmziit.git
cd schirmziit/deploy
cp .env.example .env      # set POSTGRES_PASSWORD and PUBLIC_URL
docker compose up -d
```

`PUBLIC_URL` is the address phones and browsers actually reach, and it is baked
into every pairing QR code — a wrong value gives you phones that pair once and
never sync again. The app binds to `127.0.0.1:8080`; terminate TLS in front of
it. The first account you register wins, then registration closes
(`ALLOW_REGISTRATION`). [`deploy/README.md`](deploy/README.md) has the rest.

Measuring a phone still needs the agent installed on it, and that is where the
platform matrix matters. Neither store carries it yet. The Android release is a
signed APK on the releases page — installable, but not updated for you, since
nothing is watching for a new one. An iPhone can only be measured by someone who
builds and installs the app themselves until Apple grants Family Controls
(Distribution).

## Work on it

One command installs everything — the toolchain is declared in `flake.nix`, so
nix fetches Rust with every target, the Android SDK and NDK, jdk, node, pnpm and
xcodegen. Xcode is the exception: Apple ships it through the App Store only, and
the iOS scripts say so if it is missing.

```sh
bin/setup     # once: nix, then the dev shell
bin/doctor    # what this machine has, and what it lacks
bin/dev       # api :8099, dashboard :5173, site :4321
bin/check     # every gate, all of them even after one fails
```

Nothing needs a special terminal — every script re-enters the nix dev shell
itself. If you would rather have the shell always on, `direnv allow` once and
entering the directory is enough; `.envrc` pulls in nix-direnv, so the shell is
cached and only rebuilt when `flake.nix` or `flake.lock` changes. direnv ships
hooks for bash, zsh and fish; nushell needs the hook written by hand, and
`docs/direnv-nushell.md` has one that also unsets what the shell added when you
leave. The gates live in the `justfile` and can be run directly
(`just rust-check web-check android-check ios-check`); `bin/*` are thin wrappers
over them. `bin/android-install` and `bin/ios-install` put a build on a real
phone over wifi, and `bin/record` re-records the screen goldens, deliberately.

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
