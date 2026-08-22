package ch.jorisda.nestling.agent.power

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Seam over PowerManager so the hint logic stays testable on the JVM. */
interface PowerStatus {
    fun isIgnoringOptimisations(): Boolean
    fun requestExemption(context: Context)
}

class AndroidPowerStatus(private val context: Context) : PowerStatus {

    override fun isIgnoringOptimisations(): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    /**
     * Opens the system dialog. Android deliberately gives no API to grant this
     * silently, and the request must be user-visible — which suits an app whose
     * whole premise is that the child can see what it does.
     */
    override fun requestExemption(context: Context) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        context.startActivity(intent)
    }
}
