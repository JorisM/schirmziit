#!/usr/bin/env bash
# The privacy manifest has to describe this app, not a template.
#
# App Store Connect rejects an upload with no `PrivacyInfo.xcprivacy`, and it
# accepts any manifest at all — including one that declares an API the app never
# touches, or omits one it does. Both are wrong in the same direction: the
# privacy report Apple shows a family is generated from this file, so a manifest
# nobody checks becomes a claim nobody can trust.
#
# So this fails the build when the file and the sources disagree, in either
# direction. `just ios-check` runs it before the build, the way `android-check`
# runs check-no-content.sh.
set -euo pipefail

manifest="ios/Sources/Resources/PrivacyInfo.xcprivacy"
# Everything that ships inside Schirmziit.app: the app, the framework it embeds,
# and both DeviceActivity extensions. Tests are not shipped and are left out.
sources="ios/Sources ios/AgentSources ios/AgentShared ios/AgentMonitor ios/AgentReport"

if [ ! -f "$manifest" ]; then
    echo "FAIL: $manifest is missing — App Store Connect rejects an upload without it" >&2
    exit 1
fi

plutil -lint "$manifest" >/dev/null

json=$(plutil -convert json -o - "$manifest")

# Apple's required-reason APIs, as the patterns that would appear in a source
# file. Comment lines are excluded for the same reason check-no-content.sh
# excludes them: several comments here name UserDefaults to explain why the
# keychain is used instead, and a scan that trips on its own explanation gets
# deleted.
categories=(
    "NSPrivacyAccessedAPICategoryFileTimestamp:creationDate|modificationDate|fileModificationDate|contentModificationDateKey|creationDateKey|getattrlist|getattrlistbulk|fgetattrlist|[^a-zA-Z](stat|fstat|lstat|fstatat)\("
    "NSPrivacyAccessedAPICategorySystemBootTime:systemUptime|mach_absolute_time|mach_continuous_time"
    "NSPrivacyAccessedAPICategoryDiskSpace:volumeAvailableCapacity|volumeTotalCapacity|systemFreeSize|systemSize|statfs|statvfs"
    "NSPrivacyAccessedAPICategoryActiveKeyboards:activeInputModes"
    "NSPrivacyAccessedAPICategoryUserDefaults:UserDefaults|AppStorage"
)

fail=0
for entry in "${categories[@]}"; do
    category="${entry%%:*}"
    pattern="${entry#*:}"

    hits=$(grep -rnE "$pattern" $sources --include='*.swift' \
        | grep -vE ':[[:space:]]*(///|//|\*|/\*)' || true)
    declared=$(grep -c "\"$category\"" <<<"$json" || true)

    if [ -n "$hits" ] && [ "$declared" -eq 0 ]; then
        echo "$hits"
        echo "FAIL: the sources above use $category, which $manifest does not declare" >&2
        fail=1
    fi
    if [ -z "$hits" ] && [ "$declared" -gt 0 ]; then
        echo "FAIL: $manifest declares $category, which no shipped source uses" >&2
        fail=1
    fi
done

# This product measures how long and when, never who else is watching. Tracking
# is the one answer that can never become yes without a decision nobody would
# make by accident, so it is asserted rather than trusted.
if ! grep -q '"NSPrivacyTracking":false' <<<"$json"; then
    echo "FAIL: NSPrivacyTracking must be false — Schirmziit tracks nobody" >&2
    fail=1
fi
if ! grep -q '"NSPrivacyTrackingDomains":\[\]' <<<"$json"; then
    echo "FAIL: NSPrivacyTrackingDomains must be empty — there is no tracking domain" >&2
    fail=1
fi

[ "$fail" -eq 0 ] || exit 1

echo "ok: the privacy manifest matches the shipped sources"
