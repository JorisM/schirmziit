#!/usr/bin/env bash
# release-please writes four version strings from one merged pull request, and
# it fails the way renovate.json fails: rename a file or move a marker comment
# and the release still succeeds while two of the four versions quietly stop
# moving. Validating the JSON does not catch that — a config can be perfectly
# valid and point at nothing. So assert that every target still resolves, and
# that all of them still agree with the manifest.
set -euo pipefail

cd "$(dirname "$0")/.."

config=release-please-config.json
manifest=.release-please-manifest.json
fail=0
note() { echo "FAIL: $*" >&2; fail=1; }

for f in "$config" "$manifest"; do
    if [ ! -f "$f" ]; then
        note "$f is missing"
    elif ! jq -e . "$f" >/dev/null 2>&1; then
        note "$f is not valid JSON"
    fi
done
[ "$fail" -eq 0 ] || exit 1

# The version release-please believes the repo is at. Everything else is
# compared against it: a manifest that has drifted numbers the next release
# from the wrong base, and nothing else in the repo would notice.
version=$(jq -r '.["."]' "$manifest")
if ! [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    note "the manifest version \"$version\" is not a semver"
    exit 1
fi

# The workspace version, which the rust strategy owns. First `version = ` in the
# file: it sits under [workspace.package], above [workspace.dependencies] whose
# versions are all inline.
cargo_version=$(sed -n 's/^version = "\(.*\)"$/\1/p' Cargo.toml | head -1)
[ "$cargo_version" = "$version" ] \
    || note "Cargo.toml says $cargo_version, the manifest says $version"

grep -qF "\"$version\"" Cargo.lock \
    || note "Cargo.lock carries no $version entry, so the lock file has drifted from Cargo.toml"

while read -r type path; do
    if [ ! -f "$path" ]; then
        note "extra-files entry \"$path\" does not exist"
        continue
    fi
    case "$type" in
        generic)
            grep -qF 'x-release-please-version' "$path" \
                || note "$path has no x-release-please-version marker, so release-please would not touch it"
            grep -qF "\"$version\" // x-release-please-version" "$path" \
                || note "the marked line in $path does not carry $version"
            ;;
        json)
            found=$(jq -r '.version // "«absent»"' "$path")
            [ "$found" = "$version" ] \
                || note "$path version is $found, expected $version"
            ;;
        yaml)
            grep -qF "MARKETING_VERSION: \"$version\"" "$path" \
                || note "$path has no MARKETING_VERSION: \"$version\""
            ;;
        *)
            note "no check is written for extra-files type \"$type\" ($path)"
            ;;
    esac
done < <(jq -r '.packages["."]["extra-files"][] | "\(.type) \(.path)"' "$config")

[ "$fail" -eq 0 ] || exit 1
echo "ok: every version release-please writes is at $version"
