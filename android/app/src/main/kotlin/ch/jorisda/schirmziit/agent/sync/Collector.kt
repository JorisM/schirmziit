package ch.jorisda.schirmziit.agent.sync

import ch.jorisda.schirmziit.agent.core.CoreBridge
import ch.jorisda.schirmziit.agent.core.OpenApp
import ch.jorisda.schirmziit.agent.core.PlaybackCarry
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
     * Null when background listening is not wired up at all (older builds and
     * tests). Absent or ungranted both mean the same thing on the wire:
     * background_measured = false, which reads as "we do not know".
     */
    private val playback: PlaybackReader? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val tz: () -> String = { java.util.TimeZone.getDefault().id },
) {
    /** Reads since the carry-over watermark, queues hours, returns how many. */
    fun collect(): Int {
        val now = nowMillis()
        val carry = dao.carryOver()
        // Always look back a full window, and further if an app has been open
        // longer. Starting at the carry-over watermark alone re-derives only
        // PART of the current hour, and because both the queue and the server
        // replace an hour rather than adding to it, the shorter recomputation
        // silently overwrites the fuller one — totals shrink between syncs.
        // Re-reading an already-collected span is harmless: hours are keyed, and
        // a re-opened session starts at the same instant so it adds nothing.
        val from = minOf(carry?.sinceMillis ?: Long.MAX_VALUE, now - DEFAULT_LOOKBACK_MS)

        val events = source.events(from, now)
        dao.appendRaw(events.map { RawEventRow(atMillis = it.atMillis, json = it.kind.toString()) })
        dao.pruneRawBefore(now - RAW_RETENTION_MS)

        val playbackCarry = dao.playbackCarry()
        val stitched = bridge.stitch(
            prevOpen = carry?.let { OpenApp(it.packageName, it.sinceMillis) },
            prevPlayback = playbackCarry?.let {
                PlaybackCarry(it.playing, it.screenOff, it.sinceMillis)
            },
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
            backgroundMeasured = playback?.hasPermission() == true,
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
        const val DEFAULT_LOOKBACK_MS = 2 * 60 * 60 * 1000L
        const val RAW_RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
