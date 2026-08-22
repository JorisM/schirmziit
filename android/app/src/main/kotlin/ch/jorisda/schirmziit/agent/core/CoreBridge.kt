package ch.jorisda.schirmziit.agent.core

import ch.jorisda.schirmziit.core.EventKindFfi
import ch.jorisda.schirmziit.core.OpenAppFfi
import ch.jorisda.schirmziit.core.PendingHourFfi
import ch.jorisda.schirmziit.core.RawEventFfi
import ch.jorisda.schirmziit.core.SessionFfi
import ch.jorisda.schirmziit.core.applyIngestResult
import ch.jorisda.schirmziit.core.bucketSessions
import ch.jorisda.schirmziit.core.ingestBody
import ch.jorisda.schirmziit.core.planNextSync
import ch.jorisda.schirmziit.core.stitchEvents

/** Domain-shaped aliases so the rest of the app never imports generated code. */
sealed interface EventKind {
    data class Resumed(val packageName: String) : EventKind
    data class Paused(val packageName: String) : EventKind
    data object ScreenOff : EventKind
    data object Unlock : EventKind
}

data class RawEvent(val atMillis: Long, val kind: EventKind)
data class Session(val packageName: String, val startMillis: Long, val endMillis: Long)
data class OpenApp(val packageName: String, val sinceMillis: Long)

data class StitchResult(
    val closed: List<Session>,
    val open: OpenApp?,
    val unlockMillis: List<Long>,
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
        events: List<RawEvent>,
        windowEndMillis: Long,
    ): StitchResult {
        val outcome = stitchEvents(
            prevOpen?.let { OpenAppFfi(it.packageName, it.sinceMillis) },
            events.map { event ->
                RawEventFfi(
                    event.atMillis,
                    when (val kind = event.kind) {
                        is EventKind.Resumed -> EventKindFfi.Resumed(kind.packageName)
                        is EventKind.Paused -> EventKindFfi.Paused(kind.packageName)
                        EventKind.ScreenOff -> EventKindFfi.ScreenOff
                        EventKind.Unlock -> EventKindFfi.Unlock
                    },
                )
            },
            windowEndMillis,
        )
        return StitchResult(
            closed = outcome.closed.map { Session(it.`package`, it.startMillis, it.endMillis) },
            open = outcome.open?.let { OpenApp(it.`package`, it.sinceMillis) },
            unlockMillis = outcome.unlockMillis,
        )
    }

    open fun bucket(
        sessions: List<Session>,
        unlockMillis: List<Long>,
        tz: String,
        labels: Map<String, String>,
        computedAtMillis: Long,
    ): List<PendingHourFfi> = bucketSessions(
        sessions.map { SessionFfi(it.packageName, it.startMillis, it.endMillis) },
        unlockMillis,
        tz,
        labels,
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
}
