# Dependencies

Renovate opens the pull requests that move dependencies. Nothing merges itself:
every PR waits for `ci.yml` and for a human. The configuration is
[`renovate.json`](../renovate.json); the run is
[`.github/workflows/renovate.yml`](../.github/workflows/renovate.yml), Mondays at
04:17 UTC and on demand.

## What is covered

| Surface | Files | Renovate manager |
| --- | --- | --- |
| Backend | `Cargo.toml`, `crates/*/Cargo.toml`, `Cargo.lock` | `cargo` |
| Dashboard | `web/package.json`, `web/pnpm-lock.yaml` | `npm` |
| Site | `site/package.json`, `site/pnpm-lock.yaml` | `npm` |
| Android | `android/gradle/libs.versions.toml`, `*.gradle.kts`, the wrapper | `gradle`, `gradle-wrapper` |
| iOS | `ios/project.yml` | `xcodegen` |
| CI | `.github/workflows/*.yml`, including `node-version` and the postgres service | `github-actions` |
| Images | `Dockerfile`, `site/Dockerfile`, `deploy/docker-compose.yml` | `dockerfile`, `docker-compose` |
| Toolchain | `flake.nix`, `flake.lock` | `nix` |

iOS needs no `Package.swift`: Renovate reads xcodegen's `project.yml` directly.
The `nix` manager is off by default because it shells out to `nix`, so
`renovate.json` turns it on explicitly.

## The shape of the PRs

Patch and minor updates are grouped per surface — one `rust` PR, one `dashboard`
PR, one `android` PR. **Every major gets its own PR**, and so do AGP, Kotlin, KSP
and the Compose BoM even on a minor: those move the Roborazzi goldens, and a
re-recorded screenshot needs a single cause to point at.

Cargo constraints stay loose on purpose (`serde = "1"`), so backend patches
arrive as `Cargo.lock` maintenance rather than manifest churn. The npm side is
the opposite — `rangeStrategy: bump` — because a caret floor nobody ever raises
is how a dependency rots while looking fine.

GitHub Actions are pinned to commit digests. The first run opens one large
"Pin dependencies" PR; after that a digest move is an ordinary weekly update.

## Held back on purpose

**TypeScript stays below 7** on both `web/` and `site/`. The 7.0 native port no
longer exposes `ts.factory`, which openapi-typescript uses to build
`web/src/api/schema.d.ts` — `just gen` dies before the parity chain runs — and
`astro check` reports the same missing API. Left alone, Renovate would reopen
that PR every week. Lift the `allowedVersions` rule once both toolchains ship a
7-compatible release.

**Astro majors are held**, for a PR of their own. `chore: update site to astro 6`
found that astro 7 does not build under pnpm — its prerender entry imports
`cookie` by bare specifier from `dist/`, where only the site's own direct
dependencies resolve, so the build dies at `generatePages`.

Be clear about the cost: three advisories (`GHSA-4g3v-8h47-v7g6`,
`GHSA-7pw4-f3q4-r2p2`, `GHSA-f48w-9m4c-m7f5`) affect astro 6.4.8 and are fixed
only in 7.x, so this pins the site to a known-vulnerable astro. The site is
static, prerendered and takes no user input, which is what makes the trade
defensible rather than free. The rule disables *majors* only, so astro 6 patches
still arrive, and the blocked update stays listed on the dependency dashboard
instead of disappearing.

An `allowedVersions: "<7"` rule is not enough here, and it is worth knowing why:
Renovate's vulnerability handling appends its own package rule with
`allowedVersions: ">=7.1.0"` after the repository's, so the PR came back anyway.
Only `enabled: false` on the major survives it.

## `RENOVATE_TOKEN`

The workflow authenticates with a repository secret `RENOVATE_TOKEN`, not with
the workflow's own `GITHUB_TOKEN`. This is not optional: a PR opened with
`GITHUB_TOKEN` does not start `ci.yml`, so the dependency PRs would arrive with
no test results — the one thing this setup exists to avoid.

Create a classic PAT with `repo` and `workflow` scope, or a fine-grained token
with Contents, Pull requests and Workflows write, and store it as
`RENOVATE_TOKEN`. Without it the scheduled run fails immediately.

## The gate

    just renovate-check          # or: scripts/check-renovate.sh

It validates the schema *and* runs a real extraction against the checkout
(`renovate --platform=local --dry-run=extract`, no token, no writes), then fails
if any expected manager matched nothing. Schema validation alone would not catch
that: a perfectly valid config can match zero files, and then the dependencies
go on looking current because nothing is reading them any more.

It reads the checkout through git, so **`renovate.json` must be staged or
committed** — an unstaged config is invisible to it, and the run silently falls
back to defaults. The script says so rather than passing.

The gate is not part of `just check` because it downloads Renovate and needs the
network; CI runs it on every pull request.

What it does *not* prove is the grouping — for that, run the whole thing
manually against GitHub:

    gh workflow run renovate.yml -f dryRun=true -f logLevel=debug
