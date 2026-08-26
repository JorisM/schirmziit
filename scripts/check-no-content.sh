#!/usr/bin/env bash
# The notification grant exists for MediaSessionManager and nothing else.
#
# MediaController hands out track titles and artwork, and a NotificationListener
# can read every notification on the phone. This product measures how long and
# when, never what. That guarantee has to be structural, not a promise in a
# comment, so this fails the build if any main source reaches for either.
set -euo pipefail

sources="android/app/src/main/kotlin"
banned='MediaMetadata|getActiveNotifications|activeNotifications|getNotification\(|Notification\.EXTRA_'

# Comment lines are excluded: the seam's own comments name what they exist to
# keep out, and a scan that trips on its own explanation gets deleted.
hits=$(grep -rnE "$banned" "$sources" | grep -vE ':[[:space:]]*(\*|//|/\*)' || true)
if [ -n "$hits" ]; then
    echo "$hits"
    echo "FAIL: background listening must never read notification or media content" >&2
    exit 1
fi

# Both notification callbacks must stay empty bodies. Anything other than
# `= Unit` means the service started processing notifications.
listener="$sources/ch/jorisda/schirmziit/agent/playback/PlaybackListener.kt"
for callback in onNotificationPosted onNotificationRemoved; do
    if ! grep -qE "override fun $callback\(.*\) = Unit" "$listener"; then
        echo "FAIL: $callback must stay an empty override in $listener" >&2
        exit 1
    fi
done

echo "ok: no notification or media content surface in android main sources"
