package ch.jorisda.schirmziit.agent.usage

import ch.jorisda.schirmziit.agent.core.RawEvent

/** Replays recorded events so the whole pipeline runs on the JVM. */
class FakeUsageSource(
    private val scripted: List<RawEvent>,
    private val labelMap: Map<String, String> = emptyMap(),
    private val permitted: Boolean = true,
) : UsageSource {
    override fun events(fromMillis: Long, toMillis: Long): List<RawEvent> =
        scripted.filter { it.atMillis in fromMillis..toMillis }

    override fun labels(packages: Set<String>): Map<String, String> =
        packages.associateWith { labelMap[it] ?: it }

    override fun hasPermission(): Boolean = permitted
}
