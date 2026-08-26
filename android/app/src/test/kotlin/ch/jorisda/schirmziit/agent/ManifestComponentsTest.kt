package ch.jorisda.schirmziit.agent

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every class the manifest names has to exist.
 *
 * A leading-dot component name is resolved against the *namespace*, not against
 * the directory it happens to sit in, so `.agent.playback.PlaybackListener`
 * under namespace `ch.jorisda.schirmziit.agent` asks for
 * `…agent.agent.playback.PlaybackListener` — one `agent` too many. Nothing in
 * the build complains: the manifest merger does not resolve classes, and no
 * unit test instantiates a component the way the system does. The phone finds
 * out, at launch, with `ClassNotFoundException`, and the collector is dead.
 *
 * That is the exact shape of failure the rest of this codebase guards against,
 * so it gets a test rather than a careful reading.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestComponentsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `every component the manifest declares resolves to a real class`() {
        val flags =
            PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS
        val info = context.packageManager.getPackageInfo(context.packageName, flags)

        val declared =
            buildList {
                info.applicationInfo?.className?.let { add(it) }
                info.activities?.forEach { add(it.name) }
                info.services?.forEach { add(it.name) }
                info.receivers?.forEach { add(it.name) }
                info.providers?.forEach { add(it.name) }
            }

        // Guard the guard: an empty list would make this test pass while
        // asserting nothing at all.
        assertTrue("the manifest declared no components — this test read nothing", declared.size >= 3)

        val missing =
            declared.filter { name ->
                runCatching { Class.forName(name, false, javaClass.classLoader) }.isFailure
            }

        assertTrue("manifest names classes that do not exist: $missing", missing.isEmpty())
    }
}
