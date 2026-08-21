container_engine := if `uname` == "Darwin" { "docker" } else { "podman" }

check:
    cargo fmt --check
    cargo clippy --all-targets -- -D warnings
    cargo test

fmt:
    cargo fmt

# Uses podman on shire, docker on the Mac; both accept the same arguments.
db-up:
    {{ container_engine }} run -d --name nestling-pg -e POSTGRES_PASSWORD=nestling -e POSTGRES_DB=nestling -p 5433:5432 docker.io/library/postgres:18
    @echo 'export DATABASE_URL=postgres://postgres:nestling@localhost:5433/nestling'

db-down:
    {{ container_engine }} rm -f nestling-pg
