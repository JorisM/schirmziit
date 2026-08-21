# nestling android agent

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
- JVM unit tests need a **host** build of the core (`libnestling_core.dylib` in
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

`nestling/.env` (gitignored) carries the local `DATABASE_URL` and
`SQLX_OFFLINE=true`.

## Build

    just android-check     # rust core for android + gradle unit tests
    cd android && ./gradlew assembleDebug
    adb install -r app/build/outputs/apk/debug/app-debug.apk
