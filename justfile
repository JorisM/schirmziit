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
