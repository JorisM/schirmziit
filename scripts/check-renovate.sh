#!/usr/bin/env bash
# Renovate is the only thing that bumps dependencies in this repo, and it fails
# quietly: a manager that stops matching its file reports nothing, and the deps
# go on looking current because nobody is reading them any more. Validating the
# schema does not catch that — a config can be perfectly valid and match zero
# files. So assert the extraction itself.
#
# `--platform=local` reads this checkout instead of the GitHub API, so it needs
# no token and writes nothing. It lists files through git, though, which is why
# an unstaged renovate.json is invisible to it — hence the check below.
set -euo pipefail

cd "$(dirname "$0")/.."

if ! git ls-files --error-unmatch renovate.json >/dev/null 2>&1; then
    echo "FAIL: renovate.json is not staged or committed, so --platform=local cannot see it" >&2
    exit 1
fi

npx --yes --package renovate -- renovate-config-validator --strict

# One per surface. github-actions carries the workflows *and* the postgres
# service image; xcodegen is the iOS SPM packages, which have no Package.swift.
expected=(cargo docker-compose dockerfile github-actions gradle gradle-wrapper nix npm xcodegen)

log=$(mktemp)
trap 'rm -f "$log"' EXIT

if ! LOG_LEVEL=info RENOVATE_PLATFORM=local \
    npx --yes --package renovate -- renovate --dry-run=extract >"$log" 2>&1; then
    cat "$log" >&2
    echo "FAIL: renovate could not extract dependencies from this checkout" >&2
    exit 1
fi

failed=0
for manager in "${expected[@]}"; do
    if ! grep -q "\"$manager\": {\"fileCount\"" "$log"; then
        echo "FAIL: the $manager manager matched no files" >&2
        failed=1
    fi
done

if [ "$failed" -ne 0 ]; then
    sed -n '/Dependency extraction complete/,/^ *}/p' "$log" >&2
    exit 1
fi

sed -n '/Dependency extraction complete/,/^ *}/p' "$log"
echo "ok: every dependency surface is covered by a renovate manager"
