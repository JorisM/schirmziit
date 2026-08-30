package ch.jorisda.schirmziit.agent.core

import ch.jorisda.schirmziit.core.DayDetailFfi
import ch.jorisda.schirmziit.core.DayTotalFfi
import ch.jorisda.schirmziit.core.EventKindFfi
import ch.jorisda.schirmziit.core.OpenAppFfi
import ch.jorisda.schirmziit.core.PlaybackCarryFfi
import ch.jorisda.schirmziit.core.PendingHourFfi
import ch.jorisda.schirmziit.core.RawEventFfi
import ch.jorisda.schirmziit.core.SessionFfi
import ch.jorisda.schirmziit.core.applyIngestResult
import ch.jorisda.schirmziit.core.bucketSessions
import ch.jorisda.schirmziit.core.ingestBody
import ch.jorisda.schirmziit.core.localHourStartMillis
import ch.jorisda.schirmziit.core.parseDayDetail
import ch.jorisda.schirmziit.core.parseDayStrip
import ch.jorisda.schirmziit.core.planNextSync
import ch.jorisda.schirmziit.core.stitchEvents

/** Domain-shaped aliases so the rest of the app never imports generated code. */
sealed interface EventKind {
    data class Resumed(val packageName: String) : EventKind
    data class Paused(val packageName: String) : EventKind
    data object ScreenOff : EventKind
    data object ScreenOn : EventKind
    data object Unlock : EventKind

    /**
     * A media session started or stopped playing. Package and instant only —
     * see `agent.playback.PlaybackState` for why nothing else can travel here.
     */
    data class PlaybackStarted(val packageName: String) : EventKind
    data class PlaybackStopped(val packageName: String) : EventKind
}

data class RawEvent(val atMillis: Long, val kind: EventKind)
data class Session(val packageName: String, val startMillis: Long, val endMillis: Long)
data class OpenApp(val packageName: String, val sinceMillis: Long)

/**
 * Playback state at a window boundary. Persisted and handed back on the next
 * call, exactly like [OpenApp], or a stretch spanning two syncs is lost.
 */
data class PlaybackCarry(
    val playing: String?,
    val screenOff: Boolean,
    val sinceMillis: Long?,
)

data class StitchResult(
    val closed: List<Session>,
    val open: OpenApp?,
    val unlockMillis: List<Long>,
    /** Media playing with the screen off. Never overlaps [closed]. */
    val background: List<Session>,
    val playback: PlaybackCarry,
)

data class SyncPlan(val send: List<PendingHourFfi>, val deferred: List<PendingHourFfi>)

/**
 * The single seam onto the Rust core. Everything above this class works with the
 * types declared here, so regenerating bindings cannot ripple through the app,
 * and tests can substitute a fake without loading a native library.
 */
open class CoreBridge {

    open fun stitch(
        prevOpen: OpenApp?,
        prevPlayback: PlaybackCarry?,
        events: List<RawEvent>,
        windowEndMillis: Long,
    ): StitchResult {
        val outcome = stitchEvents(
            prevOpen?.let { OpenAppFfi(it.packageName, it.sinceMillis) },
            prevPlayback?.let { PlaybackCarryFfi(it.playing, it.screenOff, it.sinceMillis) },
            events.map { event ->
                RawEventFfi(
                    event.atMillis,
                    when (val kind = event.kind) {
                        is EventKind.Resumed -> EventKindFfi.Resumed(kind.packageName)
                        is EventKind.Paused -> EventKindFfi.Paused(kind.packageName)
                        EventKind.ScreenOff -> EventKindFfi.ScreenOff
                        EventKind.ScreenOn -> EventKindFfi.ScreenOn
                        EventKind.Unlock -> EventKindFfi.Unlock
                        is EventKind.PlaybackStarted -> EventKindFfi.PlaybackStarted(kind.packageName)
                        is EventKind.PlaybackStopped -> EventKindFfi.PlaybackStopped(kind.packageName)
                    },
                )
            },
            windowEndMillis,
        )
        return StitchResult(
            closed = outcome.closed.map { Session(it.`package`, it.startMillis, it.endMillis) },
            open = outcome.open?.let { OpenApp(it.`package`, it.sinceMillis) },
            unlockMillis = outcome.unlockMillis,
            background = outcome.background.map {
                Session(it.`package`, it.startMillis, it.endMillis)
            },
            playback = PlaybackCarry(
                playing = outcome.playback.playing,
                screenOff = outcome.playback.screenOff,
                sinceMillis = outcome.playback.sinceMillis,
            ),
        )
    }

    /** Start of the local hour containing [atMillis], in the given zone. */
    open fun localHourStart(atMillis: Long, tz: String): Long = localHourStartMillis(atMillis, tz)

    open fun bucket(
        sessions: List<Session>,
        background: List<Session>,
        unlockMillis: List<Long>,
        tz: String,
        labels: Map<String, String>,
        backgroundMeasured: Boolean,
        computedAtMillis: Long,
    ): List<PendingHourFfi> = bucketSessions(
        sessions.map { SessionFfi(it.packageName, it.startMillis, it.endMillis) },
        background.map { SessionFfi(it.packageName, it.startMillis, it.endMillis) },
        unlockMillis,
        tz,
        labels,
        backgroundMeasured,
        computedAtMillis,
    )

    open fun planSync(
        pending: List<PendingHourFfi>,
        maxRows: UInt = 500u,
        maxBytes: UInt = 1_000_000u,
    ): SyncPlan = planNextSync(pending, maxRows, maxBytes).let { SyncPlan(it.send, it.deferred) }

    open fun buildIngestBody(hours: List<PendingHourFfi>, deviceTimeMillis: Long): String =
        ingestBody(hours, deviceTimeMillis)

    /** Throws on an unparseable response — never silently drops the queue. */
    open fun applyResult(
        pending: List<PendingHourFfi>,
        responseJson: String,
    ): List<PendingHourFfi> = applyIngestResult(pending, responseJson)

    open fun dayStrip(json: String): List<DayTotalFfi> = parseDayStrip(json)

    open fun dayDetail(json: String): DayDetailFfi = parseDayDetail(json)
}
