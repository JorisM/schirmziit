package ch.jorisda.schirmziit.agent.mytime

import ch.jorisda.schirmziit.core.AppTotalFfi

data class AppSplit(val shown: List<AppTotalFfi>, val brief: List<AppTotalFfi>)

/**
 * Apps worth a row of their own, and the glances that are not.
 *
 * The same rule the parent's list uses: a child and a parent must see the same
 * numbers, so this cannot diverge from `Formatting.splitApps` on iOS or
 * `splitApps` on the web.
 */
fun splitApps(apps: List<AppTotalFfi>): AppSplit {
    val shown = mutableListOf<AppTotalFfi>()
    val brief = mutableListOf<AppTotalFfi>()
    for (app in apps) {
        // Rounded, not raw: the row would render "0 s", which says nothing at all.
        val seconds = Math.round(app.foregroundMs / 1_000.0)
        if (seconds == 0L) continue
        if (app.foregroundMs < 60_000) brief.add(app) else shown.add(app)
    }
    return AppSplit(shown, brief)
}
