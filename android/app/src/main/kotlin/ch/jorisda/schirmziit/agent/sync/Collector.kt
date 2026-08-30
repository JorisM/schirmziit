package ch.jorisda.schirmziit.agent.sync

import ch.jorisda.schirmziit.agent.core.CoreBridge
import ch.jorisda.schirmziit.agent.core.EventKind
import ch.jorisda.schirmziit.agent.core.OpenApp
import ch.jorisda.schirmziit.agent.core.PlaybackCarry
import ch.jorisda.schirmziit.agent.core.RawEvent
import ch.jorisda.schirmziit.agent.playback.PlaybackReader
import ch.jorisda.schirmziit.agent.store.AgentSettings
import ch.jorisda.schirmziit.agent.store.CarryOverRow
import ch.jorisda.schirmziit.agent.store.PendingHourRow
import ch.jorisda.schirmziit.agent.store.PlaybackCarryRow
import ch.jorisda.schirmziit.agent.store.QueueDao
import ch.jorisda.schirmziit.agent.store.RawEventRow
import ch.jorisda.schirmziit.agent.usage.UsageSource
import ch.jorisda.schirmziit.core.PendingAppFfi
import ch.jorisda.schirmziit.core.PendingHourFfi
import org.json.JSONObject

data class SyncOutcome(val sent: Int, val remaining: Int, val error: String?)

/**
 * I/O only. Every decision — what a session is, which hour it belongs to, what
 * goes in the next batch, what to drop afterwards — is a call into the core.
 */
class Collector(
    private val bridge: CoreBridge,
    private val source: UsageSource,
    private val dao: QueueDao,
    private val store: AgentSettings,
    /**
     * Required, and deliberately without a default: it used to have one, and
     * SyncWorker — the only caller that matters — quietly took it. Every hour a
     * granted phone ever sent said background_measured = false.
     */
    private val playback: PlaybackReader,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val tz: () -> String = { java.util.TimeZone.getDefault().id },
) {
    /** Reads since the carry-over watermark, queues hours, returns how many. */
    fun collect(): Int {
        val now = nowMillis()
        val carry = dao.carryOver()
        val playbackCarry = dao.playbackCarry()
        // Always look back a full window, and further if something has been open
        // longer — an app in the foreground, or a stretch of listening. Starting
        // at a watermark alone re-derives only PART of an hour, and because both
        // the queue and the server replace an hour rather than adding to it, the
        // shorter recomputation silently overwrites the fuller one — totals
        // shrink between syncs. Re-reading an already-collected span is
        // harmless: hours are keyed, and a re-opened session starts at the same
        // instant so it adds nothing.
        //
        // Listening is the watermark that reaches furthest. A stretch is only
        // counted when it CLOSES, and closing it emits every hour it touched —
        // a child asleep with an audiobook closes hours the lookback left
        // behind long ago. Without this those hours arrive with the evening's
        // screen time missing and replace the version a parent already saw.
        val watermark = minOf(
            carry?.sinceMillis ?: Long.MAX_VALUE,
            playbackCarry?.sinceMillis ?: Long.MAX_VALUE,
            now - DEFAULT_LOOKBACK_MS,
        )
        // Floored to the local hour, then one hour further: an hour derived
        // from a window that starts in the middle of it is thinner than the
        // hour itself, and the extra hour is what makes a session that began
        // just before the boundary visible rather than half-counted. The
        // margin is context — nothing happened in it means nothing is queued
        // for it. A session that began even earlier than that and runs into a
        // re-derived hour is still under-counted there; closing that needs a
        // durable event log rather than a wider guess, and is not this fix.
        val from = bridge.localHourStart(watermark, tz()) - HOUR_MS

        val usage = source.events(from, now)
        dao.appendRaw(usage.map { RawEventRow(atMillis = it.atMillis, json = it.kind.toString()) })
        dao.pruneRawBefore(now - RAW_RETENTION_MS)

        // Written by PlaybackListener whenever Android woke it, which is never
        // this call. Read back over the same window as the usage events, and
        // kept for as long: an hour gets recomputed, so consuming them here
        // would empty a night that the next run still has to re-derive.
        val playbackEvents = dao.playbackEvents(from, now).map { row ->
            RawEvent(
                atMillis = row.atMillis,
                kind = if (row.started) {
                    EventKind.PlaybackStarted(row.packageName)
                } else {
                    EventKind.PlaybackStopped(row.packageName)
                },
            )
        }
        dao.prunePlaybackBefore(now - RAW_RETENTION_MS)

        // Usage first at an equal instant: a screen-off sharing a millisecond
        // with a playback start has to be seen first, or the stretch reads as
        // screen-on and background listening is counted as nothing at all.
        val events = (usage + playbackEvents).sortedBy { it.atMillis }

        // The carry says where to start reading; the events say what happened.
        // Handing the carry's own instants back to the stitch as well would date
        // a stretch from the END of the last window, which now lies inside this
        // one — and a stretch already "open" at an instant the window has
        // rewound past swallows every transition before it. Playback state at
        // the window start comes from the log instead: whatever was playing just
        // before it opened, with no stretch running yet. A stretch that WAS
        // running is impossible here, because its own start is what pushed
        // `from` an hour further back.
        val playingAtStart = dao.playbackBefore(from)?.takeIf { it.started }?.packageName
        val stitched = bridge.stitch(
            prevOpen = carry?.let { OpenApp(it.packageName, it.sinceMillis) },
            prevPlayback = PlaybackCarry(playing = playingAtStart, screenOff = false, sinceMillis = null),
            events = events,
            windowEndMillis = now,
        )

        val labels = source.labels(
            (stitched.closed + stitched.background).map { it.packageName }.toSet(),
        )
        val hours = bridge.bucket(
            sessions = stitched.closed,
            background = stitched.background,
            unlockMillis = stitched.unlockMillis,
            tz = tz(),
            labels = labels,
            backgroundMeasured = playback.hasPermission(),
            computedAtMillis = now,
        )

        dao.upsert(
            hours.map { hour ->
                PendingHourRow(
                    hourStartMillis = hour.hourStartMillis,
                    json = bridge.buildIngestBody(listOf(hour), now),
                    computedAtMillis = hour.computedAtMillis,
                )
            },
        )

        val open = stitched.open
        if (open != null) {
            dao.setCarryOver(CarryOverRow(packageName = open.packageName, sinceMillis = open.sinceMillis))
        } else {
            dao.clearCarryOver()
        }

        // Persisted even when nothing is playing: `screenOff` is state too, and
        // losing it means the next window starts assuming the screen is on and
        // silently skips a stretch that was already running.
        dao.setPlaybackCarry(
            PlaybackCarryRow(
                playing = stitched.playback.playing,
                screenOff = stitched.playback.screenOff,
                sinceMillis = stitched.playback.sinceMillis,
            ),
        )

        return hours.size
    }

    fun sync(client: SchirmziitClient): SyncOutcome {
        val token = store.deviceToken ?: return SyncOutcome(0, dao.pendingCount(), "not paired")
        val pending = dao.pending().mapNotNull { row -> hourFromRow(row.json) }
        if (pending.isEmpty()) return SyncOutcome(0, 0, null)

        val plan = bridge.planSync(pending)
        val body = bridge.buildIngestBody(plan.send, nowMillis())

        return try {
            val response = client.ingest(token, body)
            val kept = bridge.applyResult(plan.send, response)
            val keptStarts = kept.map { it.hourStartMillis }.toSet()
            val acknowledged = plan.send.map { it.hourStartMillis }.filterNot { it in keptStarts }
            dao.delete(acknowledged)

            store.lastSyncMillis = nowMillis()
            store.lastError = null
            SyncOutcome(sent = acknowledged.size, remaining = dao.pendingCount(), error = null)
        } catch (failure: Exception) {
            // Nothing is deleted on failure. An unparseable body (captcha, proxy
            // error page) must never read as "accepted".
            val message = failure.message ?: failure::class.java.simpleName
            store.lastError = message
            SyncOutcome(sent = 0, remaining = dao.pendingCount(), error = message)
        }
    }

    /** Rebuild one PendingHourFfi from the stored request JSON. */
    private fun hourFromRow(json: String): PendingHourFfi? = runCatching {
        val hour = JSONObject(json).getJSONArray("hours").getJSONObject(0)
        val apps = hour.getJSONArray("apps")
        PendingHourFfi(
            hourStartMillis = java.time.Instant.parse(hour.getString("hour_start")).toEpochMilli(),
            tz = hour.getString("tz"),
            computedAtMillis = java.time.Instant.parse(hour.getString("computed_at")).toEpochMilli(),
            screenOnMs = hour.getLong("screen_on_ms"),
            unlockCount = hour.getInt("unlock_count"),
            backgroundMeasured = hour.optBoolean("background_measured", false),
            apps = (0 until apps.length()).map { index ->
                val app = apps.getJSONObject(index)
                PendingAppFfi(
                    `package` = app.getString("package"),
                    label = app.getString("label"),
                    foregroundMs = app.getLong("foreground_ms"),
                    launchCount = app.getInt("launch_count"),
                    backgroundMs = app.optLong("background_ms", 0L),
                )
            },
        )
    }.getOrNull()

    private companion object {
        const val HOUR_MS = 60 * 60 * 1000L
        const val DEFAULT_LOOKBACK_MS = 2 * HOUR_MS
        const val RAW_RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
