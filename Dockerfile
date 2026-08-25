# Build the dashboard first; the Rust binary embeds web/dist via rust-embed.
FROM node:24-alpine AS web
WORKDIR /build
COPY web/package.json web/pnpm-lock.yaml web/pnpm-workspace.yaml ./web/
RUN corepack enable && cd web && pnpm install --frozen-lockfile
COPY api/ ./api/
COPY web/ ./web/
# Where the dashboard sends its API calls. Empty (the default) keeps the
# self-hosted shape: this binary serves both, so relative paths work and no CORS
# is involved. The hosted build sets it to https://api.schirmziit.ch, which is a
# different origin from the page and therefore needs DASHBOARD_ORIGINS on the
# server side to match.
ARG VITE_API_BASE=""
ENV VITE_API_BASE=$VITE_API_BASE
RUN cd web && pnpm build

FROM rust:1-bookworm AS build
WORKDIR /src
COPY Cargo.toml Cargo.lock ./
COPY crates/ crates/
COPY .sqlx/ .sqlx/
COPY --from=web /build/web/dist web/dist
# The committed offline data is what lets the image build without a database,
# while keeping every query compile-time checked.
ENV SQLX_OFFLINE=true
RUN cargo build --release --bin schirmziit-server

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/target/release/schirmziit-server /usr/local/bin/schirmziit-server
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/schirmziit-server"]
