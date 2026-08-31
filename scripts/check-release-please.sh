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

# Cargo.lock IS asserted now, where it deliberately was not before. The old
# reason was that release-please does not write it (the rust strategy that
# would have is not usable here - see the "//" comment in
# release-please-config.json), so a post-release lag was legitimate and
# asserting it would have turned every release into a red main. release.yml
# refreshes the lock on the Release PR branch itself, so there is no lag left
# to be legitimate - and this is exactly the failure this script exists for: if
# that step is renamed, skipped or silently stops matching, the versions quietly
# stop moving and nothing else notices.
lock_versions=$(awk '/^name = "(schirmziit-core|schirmziit-server|copygen)"$/ { getline; print }' Cargo.lock \
    | sed -n 's/^version = "\(.*\)"$/\1/p' | sort -u)
if [ -z "$lock_versions" ]; then
    note "Cargo.lock lists none of the workspace crates, so its versions could not be checked"
elif [ "$lock_versions" != "$version" ]; then
    note "Cargo.lock carries $(echo "$lock_versions" | tr '\n' ' ')but the manifest says $version - run: cargo update --workspace --offline"
fi

# `done < <(jq ...)` would run the loop in the shell's own scope but hide a
# failing or empty jq behind it: a background pipeline's exit status never
# reaches the foreground `while`, so a renamed "packages" key or a deleted
# extra-files array both iterate zero times and read as "everything passed".
# Count first, and capture jq's own exit status, so a config that points at
# nothing fails loudly instead of quietly checking nothing.
extra_files_count=$(jq -r '.packages["."]["extra-files"] // [] | length' "$config")
if [ "$extra_files_count" -eq 0 ]; then
    note "$config has no extra-files entries under .packages[\".\"], so nothing but Cargo.toml would be checked"
fi

entries=$(jq -r '.packages["."]["extra-files"] // [] | .[] | "\(.type) \(.path)"' "$config") \
    || note "$config extra-files could not be read by jq"

while read -r type path; do
    [ -n "$type" ] || continue
    if [ ! -f "$path" ]; then
        note "extra-files entry \"$path\" does not exist"
        continue
    fi
    case "$type" in
        toml)
            # Same file and the same value the Cargo.toml-vs-manifest
            # comparison above already checked; this is release-please's own
            # account of where it writes it, under [workspace.package], so it
            # must resolve too, not just parse as TOML.
            grep -qF "version = \"$version\"" "$path" \
                || note "$path has no version = \"$version\" line for the extra-files entry to write"
            ;;
        generic)
            # The comment syntax is the file's own — `//` in build.gradle.kts,
            # `#` in project.yml — and release-please does not care which: it
            # looks for the marker and replaces the version on that line. So
            # check the same two things it acts on, and nothing about the
            # comment around them.
            marked=$(grep -F 'x-release-please-version' "$path" || true)
            if [ -z "$marked" ]; then
                note "$path has no x-release-please-version marker, so release-please would not touch it"
            elif ! grep -qF "\"$version\"" <<< "$marked"; then
                note "the marked line in $path does not carry \"$version\": $marked"
            fi
            ;;
        json)
            found=$(jq -r '.version // "«absent»"' "$path")
            [ "$found" = "$version" ] \
                || note "$path version is $found, expected $version"
            ;;
        yaml)
            # Not a missing check: a refusal. release-please's yaml updater
            # re-emits the whole document from its parse tree rather than
            # patching the line the jsonpath names, so a "yaml" entry silently
            # strips every comment in the file and requotes every scalar —
            # which is what it did to ios/project.yml in the 0.2.0 release.
            # A marked line and "generic" do the same job to one line.
            note "$path is an extra-files \"yaml\" entry; use \"generic\" with an x-release-please-version marker instead, or the yaml updater will rewrite the whole file"
            ;;
        *)
            note "no check is written for extra-files type \"$type\" ($path)"
            ;;
    esac
done <<< "$entries"

[ "$fail" -eq 0 ] || exit 1
echo "ok: every version release-please writes is at $version"
