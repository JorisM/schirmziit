container_engine := if `uname` == "Darwin" { "docker" } else { "podman" }

export DATABASE_URL := env("DATABASE_URL", "postgres://postgres:schirmziit@localhost:5433/schirmziit")

# The whole gate: formatting, lints, SQL-vs-schema, Rust->spec->TypeScript
# parity, and every test on both sides.
check: rust-check web-check

rust-check:
    cargo fmt --check
    cargo clippy --all-targets -- -D warnings
    cargo sqlx prepare --workspace --check
    just openapi-check
    cargo test

fmt:
    cargo fmt

# Regenerate the sqlx offline data. Committed, so the Docker build and CI need
# no database and SQL stays compile-time checked.
prepare:
    cargo sqlx prepare --workspace

openapi:
    cargo run --quiet --bin export-openapi > api/openapi.json

openapi-check: openapi
    git diff --exit-code api/openapi.json

gen:
    cd web && pnpm openapi-typescript ../api/openapi.json -o src/api/schema.d.ts

gen-check: gen
    git diff --exit-code web/src/api/schema.d.ts

# The error copy: one TOML in four languages becomes the dashboard dictionary,
# the iOS .strings and the Android string resources.
gen-copy:
    cargo run --quiet -p copygen

gen-copy-check: gen-copy
    git diff --exit-code web/src/i18n/errors.ts ios/Sources/Resources android/app/src/main/res

web-check: gen-check gen-copy-check
    cd web && pnpm tsc -b --noEmit && pnpm vitest run

# Uses podman on shire, docker on the Mac; both accept the same arguments.
db-up:
    {{ container_engine }} run -d --name schirmziit-pg -e POSTGRES_PASSWORD=schirmziit -e POSTGRES_DB=schirmziit -p 5433:5432 docker.io/library/postgres:18
    @echo 'export DATABASE_URL=postgres://postgres:schirmziit@localhost:5433/schirmziit'

db-down:
    {{ container_engine }} rm -f schirmziit-pg

# ─── Android agent ───────────────────────────────────────────────────
# $HOME/.cargo/bin must precede /opt/homebrew/bin: Homebrew's rust shadows
# rustup and has no Android std. See android/README.md.
android-bindings:
    cargo ndk -t arm64-v8a -t x86_64 -o android/app/src/main/jniLibs build -p schirmziit-core --release
    cargo run --quiet --bin uniffi-bindgen -- generate \
        --library target/aarch64-linux-android/release/libschirmziit_core.so \
        --language kotlin --out-dir android/app/src/main/kotlin
    # JVM unit tests load the core through JNA, which needs a HOST build in an
    # <os>-<arch> resource dir. On an Intel Mac use darwin-x86-64; on Linux CI,
    # linux-x86-64 with the .so. The build re-jars that dir (testNativeLibs):
    # AGP drops **/*.so from the merged test resource dir, so only the jar puts
    # a .so on the unit test classpath.
    cargo build -p schirmziit-core --release
    mkdir -p android/app/src/test/resources/darwin-aarch64
    cp target/release/libschirmziit_core.dylib android/app/src/test/resources/darwin-aarch64/

android-check: android-bindings
    bash scripts/check-no-content.sh
    cd android && ./gradlew test

# ─── iOS ─────────────────────────────────────────────────────────────
# Inside `nix develop` the darwin stdenv owns the C toolchain: DEVELOPER_DIR and
# SDKROOT point at a macOS-only apple-sdk, and `cc` is a wrapper pinned to
# arm64-apple-darwin. That produces two different failures, so there are two
# different prefixes.
#
# `apple` is for cargo cross-compiling to an iOS target, and only for those two
# lines — the host build of uniffi-bindgen wants nix's compiler. Without it,
# `xcrun --sdk iphonesimulator` finds no SDK (DEVELOPER_DIR), rustc links against
# a macOS sysroot (SDKROOT), and the link pulls in nix's macOS libiconv:
# "building for iOS Simulator, but linking in dylib built for macOS". Naming
# Xcode's clang as the linker is what avoids the last one; prepending it to PATH
# instead does not survive a PATH with a space in it.
#
# `xcb` is for xcodebuild, which reads far more of the environment than that —
# with the dev shell's, linking an appex dies on "-objc_abi_version '-Xlinker'
# not supported (expected 2)". A bare environment is the only reliable answer,
# which is also why xcodegen is not run through it: that one is a nix binary.
#
# Outside nix both are no-ops in effect — the same Xcode, the same PATH.
xcode_dir := if os() == "macos" { `DEVELOPER_DIR= xcode-select -p 2>/dev/null || true` } else { "" }
xcode_clang := xcode_dir + "/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang"
apple := if os() == "macos" { "env -u SDKROOT -u NIX_CFLAGS_COMPILE -u NIX_LDFLAGS DEVELOPER_DIR=" + xcode_dir + " CARGO_TARGET_AARCH64_APPLE_IOS_LINKER=" + xcode_clang + " CARGO_TARGET_AARCH64_APPLE_IOS_SIM_LINKER=" + xcode_clang } else { "" }
xcb := if os() == "macos" { "env -i HOME=" + env('HOME') + " USER=" + env('USER') + " TMPDIR=" + env('TMPDIR', '/tmp') + " LANG=en_US.UTF-8 PATH=/usr/bin:/bin:/usr/sbin:/sbin xcodebuild" } else { "xcodebuild" }

# The child agent and the parent viewer share the Rust core through a Swift
# xcframework. Both simulator and device slices, so `ios-check` needs no signing.
ios-core:
    {{ apple }} cargo build -p schirmziit-core --release --target aarch64-apple-ios
    {{ apple }} cargo build -p schirmziit-core --release --target aarch64-apple-ios-sim
    rm -rf ios/Generated ios/Frameworks build/ios
    mkdir -p ios/Generated build/ios/include
    cargo run --quiet --bin uniffi-bindgen -- generate \
        --library target/aarch64-apple-ios/release/libschirmziit_core.dylib \
        --language swift --out-dir ios/Generated
    cp ios/Generated/schirmziit_coreFFI.h build/ios/include/
    cp ios/Generated/schirmziit_coreFFI.modulemap build/ios/include/module.modulemap
    {{ xcb }} -create-xcframework \
        -library target/aarch64-apple-ios/release/libschirmziit_core.a -headers build/ios/include \
        -library target/aarch64-apple-ios-sim/release/libschirmziit_core.a -headers build/ios/include \
        -output ios/Frameworks/SchirmziitCoreFFI.xcframework

ios-project: ios-core
    cd ios && xcodegen generate

# Both apps, on the simulator, unsigned. Not part of `check`: CI runs on Linux.
ios-check: ios-project
    cd ios && {{ xcb }} -project Schirmziit.xcodeproj -scheme Schirmziit \
        -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test CODE_SIGNING_ALLOWED=NO

# Re-record the screen images, deliberately: delete them, then let the run write
# what is missing. Reports failures for every image it writes — that is the
# library saying "there was no reference". Check `git diff` before committing.
ios-record: ios-project
    rm -rf ios/Tests/__Snapshots__
    -cd ios && {{ xcb }} -project Schirmziit.xcodeproj -scheme Schirmziit \
        -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test CODE_SIGNING_ALLOWED=NO
    cd ios && {{ xcb }} -project Schirmziit.xcodeproj -scheme Schirmziit \
        -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test CODE_SIGNING_ALLOWED=NO

