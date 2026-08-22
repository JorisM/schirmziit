package ch.jorisda.nestling.agent.power

import android.content.Context

class FakePowerStatus(var exempt: Boolean = false) : PowerStatus {
    var exemptionRequests = 0

    override fun isIgnoringOptimisations(): Boolean = exempt

    override fun requestExemption(context: Context) {
        exemptionRequests++
        // The real dialog is the user's decision; the fake only records the ask.
    }
}
