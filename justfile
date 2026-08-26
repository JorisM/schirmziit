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

web-check: gen-check
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
# The child agent and the parent viewer share the Rust core through a Swift
# xcframework. Both simulator and device slices, so `ios-check` needs no signing.
ios-core:
    cargo build -p schirmziit-core --release --target aarch64-apple-ios
    cargo build -p schirmziit-core --release --target aarch64-apple-ios-sim
    rm -rf ios/Generated ios/Frameworks build/ios
    mkdir -p ios/Generated build/ios/include
    cargo run --quiet --bin uniffi-bindgen -- generate \
        --library target/aarch64-apple-ios/release/libschirmziit_core.dylib \
        --language swift --out-dir ios/Generated
    cp ios/Generated/schirmziit_coreFFI.h build/ios/include/
    cp ios/Generated/schirmziit_coreFFI.modulemap build/ios/include/module.modulemap
    xcodebuild -create-xcframework \
        -library target/aarch64-apple-ios/release/libschirmziit_core.a -headers build/ios/include \
        -library target/aarch64-apple-ios-sim/release/libschirmziit_core.a -headers build/ios/include \
        -output ios/Frameworks/SchirmziitCoreFFI.xcframework

ios-project: ios-core
    cd ios && xcodegen generate

# Both apps, on the simulator, unsigned. Not part of `check`: CI runs on Linux.
ios-check: ios-project
    cd ios && xcodebuild -project Schirmziit.xcodeproj -scheme Schirmziit \
        -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test CODE_SIGNING_ALLOWED=NO

# Re-record the screen images, deliberately: delete them, then let the run write
# what is missing. Reports failures for every image it writes — that is the
# library saying "there was no reference". Check `git diff` before committing.
ios-record: ios-project
    rm -rf ios/Tests/__Snapshots__
    -cd ios && xcodebuild -project Schirmziit.xcodeproj -scheme Schirmziit \
        -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test CODE_SIGNING_ALLOWED=NO
    cd ios && xcodebuild -project Schirmziit.xcodeproj -scheme Schirmziit \
        -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test CODE_SIGNING_ALLOWED=NO

