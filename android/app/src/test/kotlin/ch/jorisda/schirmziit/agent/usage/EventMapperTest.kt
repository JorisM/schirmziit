package ch.jorisda.schirmziit.agent.usage

import android.app.usage.UsageEvents
import ch.jorisda.schirmziit.agent.core.EventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventMapperTest {
    @Test
    fun `maps modern activity transitions`() {
        val resumed = EventMapper.map(UsageEvents.Event.ACTIVITY_RESUMED, "com.a", 10)
        assertEquals(EventKind.Resumed("com.a"), resumed?.kind)
        assertEquals(10L, resumed?.atMillis)

        val paused = EventMapper.map(UsageEvents.Event.ACTIVITY_PAUSED, "com.a", 20)
        assertEquals(EventKind.Paused("com.a"), paused?.kind)
    }

    @Test
    fun `maps legacy foreground transitions on pre-API29 devices`() {
        // MOVE_TO_FOREGROUND = 1, MOVE_TO_BACKGROUND = 2. Dropping these loses
        // all usage on older Android.
        assertEquals(EventKind.Resumed("com.a"), EventMapper.map(1, "com.a", 10)?.kind)
        assertEquals(EventKind.Paused("com.a"), EventMapper.map(2, "com.a", 20)?.kind)
    }

    @Test
    fun `maps screen off and unlock`() {
        assertEquals(
            EventKind.ScreenOff,
            EventMapper.map(UsageEvents.Event.SCREEN_NON_INTERACTIVE, null, 30)?.kind,
        )
        assertEquals(
            EventKind.Unlock,
            EventMapper.map(UsageEvents.Event.KEYGUARD_HIDDEN, null, 40)?.kind,
        )
    }

    @Test
    fun `ignores event types we do not model`() {
        assertNull(EventMapper.map(UsageEvents.Event.CONFIGURATION_CHANGE, "com.a", 50))
    }

    @Test
    fun `drops an activity event with no package rather than inventing one`() {
        assertNull(EventMapper.map(UsageEvents.Event.ACTIVITY_RESUMED, null, 60))
    }
}
