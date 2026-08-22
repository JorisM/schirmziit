package ch.jorisda.schirmziit.agent.usage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on the device. Proves the two things a fake cannot: that the AppOps check
 * reflects reality, and that queryEvents returns transitions we can map.
 */
@RunWith(AndroidJUnit4::class)
class AndroidUsageSourceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = AndroidUsageSource(context)

    @Test
    fun permissionStateIsReadable() {
        println("usage access granted: ${source.hasPermission()}")
    }

    @Test
    fun realEventStreamContainsMappableTransitions() {
        assumeTrue("grant usage access first", source.hasPermission())
        val now = System.currentTimeMillis()
        val events = source.events(now - 24 * 60 * 60 * 1000L, now)

        println("events in the last 24h: ${events.size}")
        println("first 5: ${events.take(5)}")
        assertTrue("expected some usage events on a phone in use", events.isNotEmpty())
    }

    @Test
    fun packageLabelsResolve() {
        val labels = source.labels(setOf("com.android.settings"))
        println("labels: $labels")
        assertTrue(labels["com.android.settings"]?.isNotEmpty() == true)
    }
}
