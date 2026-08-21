container_engine := if `uname` == "Darwin" { "docker" } else { "podman" }

export DATABASE_URL := env("DATABASE_URL", "postgres://postgres:nestling@localhost:5433/nestling")

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
    {{ container_engine }} run -d --name nestling-pg -e POSTGRES_PASSWORD=nestling -e POSTGRES_DB=nestling -p 5433:5432 docker.io/library/postgres:18
    @echo 'export DATABASE_URL=postgres://postgres:nestling@localhost:5433/nestling'

db-down:
    {{ container_engine }} rm -f nestling-pg

# ─── Android agent ───────────────────────────────────────────────────
# $HOME/.cargo/bin must precede /opt/homebrew/bin: Homebrew's rust shadows
# rustup and has no Android std. See android/README.md.
android-bindings:
    cargo ndk -t arm64-v8a -t x86_64 -o android/app/src/main/jniLibs build -p nestling-core --release
    cargo run --quiet --bin uniffi-bindgen -- generate \
        --library target/aarch64-linux-android/release/libnestling_core.so \
        --language kotlin --out-dir android/app/src/main/kotlin
    # JVM unit tests load the core through JNA, which needs a HOST build in an
    # <os>-<arch> resource dir. On an Intel Mac use darwin-x86-64; on Linux CI,
    # linux-x86-64 with the .so.
    cargo build -p nestling-core --release
    mkdir -p android/app/src/test/resources/darwin-aarch64
    cp target/release/libnestling_core.dylib android/app/src/test/resources/darwin-aarch64/

android-check: android-bindings
    cd android && ./gradlew test
