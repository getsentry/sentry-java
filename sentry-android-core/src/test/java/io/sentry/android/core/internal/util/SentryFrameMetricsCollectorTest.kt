package io.sentry.android.core.internal.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.view.Window
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.SentryOptions
import io.sentry.android.core.BuildInfoProvider
import io.sentry.test.getCtor
import io.sentry.test.getProperty
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.ref.WeakReference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class SentryFrameMetricsCollectorTest {
    private lateinit var context: Context

    private val className = "io.sentry.android.core.internal.util.SentryFrameMetricsCollector"
    private val ctorTypes = arrayOf(Context::class.java, SentryOptions::class.java, BuildInfoProvider::class.java)
    private val fixture = Fixture()

    private class Fixture {
        private val mockDsn = "http://key@localhost/proj"
        val buildInfo = mock<BuildInfoProvider> {
            whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)
        }
        val mockLogger = mock<ILogger>()
        val options = spy(SentryOptions()).apply {
            dsn = mockDsn
            isDebug = true
            setLogger(mockLogger)
        }

        val activity = mock<Activity>()
        val window = mock<Window>()
        val activity2 = mock<Activity>()
        val window2 = mock<Window>()

        var addOnFrameMetricsAvailableListenerCounter = 0
        var removeOnFrameMetricsAvailableListenerCounter = 0
        val windowFrameMetricsManager = object : SentryFrameMetricsCollector.WindowFrameMetricsManager {
            override fun addOnFrameMetricsAvailableListener(
                window: Window,
                frameMetricsAvailableListener: Window.OnFrameMetricsAvailableListener?,
                handler: Handler?
            ) {
                addOnFrameMetricsAvailableListenerCounter++
            }

            override fun removeOnFrameMetricsAvailableListener(
                window: Window,
                frameMetricsAvailableListener: Window.OnFrameMetricsAvailableListener?
            ) {
                removeOnFrameMetricsAvailableListenerCounter++
            }
        }

        fun getSut(context: Context, buildInfoProvider: BuildInfoProvider = buildInfo): SentryFrameMetricsCollector {
            whenever(activity.window).thenReturn(window)
            whenever(activity2.window).thenReturn(window2)
            addOnFrameMetricsAvailableListenerCounter = 0
            removeOnFrameMetricsAvailableListenerCounter = 0
            return SentryFrameMetricsCollector(
                context,
                options,
                buildInfoProvider,
                windowFrameMetricsManager
            )
        }
    }

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `when null param is provided, invalid argument is thrown`() {
        val ctor = className.getCtor(ctorTypes)

        assertFailsWith<IllegalArgumentException> {
            ctor.newInstance(arrayOf(null, mock<SentryOptions>(), mock()))
        }
        assertFailsWith<IllegalArgumentException> {
            ctor.newInstance(arrayOf(mock<Context>(), null, mock()))
        }
        assertFailsWith<IllegalArgumentException> {
            ctor.newInstance(arrayOf(mock<Context>(), mock<SentryOptions>(), null))
        }
    }

    @Test
    fun `collector works only on api 24+`() {
        val buildInfo = mock<BuildInfoProvider> {
            whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.M)
        }
        val collector = fixture.getSut(context, buildInfo)
        val id = collector.startCollection(mock())
        assertNull(id)
    }

    @Test
    fun `collector works only if context is instance of Application`() {
        val collector = fixture.getSut(mock())
        val id = collector.startCollection(mock())
        assertNull(id)
    }

    @Test
    fun `startCollection returns an id`() {
        val collector = fixture.getSut(context)
        val id = collector.startCollection(mock())
        assertNotNull(id)
    }

    @Test
    fun `collector calls addOnFrameMetricsAvailableListener when an activity starts`() {
        val collector = fixture.getSut(context)

        collector.startCollection(mock())
        assertEquals(0, fixture.addOnFrameMetricsAvailableListenerCounter)
        collector.onActivityStarted(fixture.activity)
        assertEquals(1, fixture.addOnFrameMetricsAvailableListenerCounter)
    }

    @Test
    fun `collector calls removeOnFrameMetricsAvailableListener when an activity stops`() {
        val collector = fixture.getSut(context)

        collector.startCollection(mock())
        collector.onActivityStarted(fixture.activity)
        assertEquals(0, fixture.removeOnFrameMetricsAvailableListenerCounter)
        collector.onActivityStopped(fixture.activity)
        assertEquals(1, fixture.removeOnFrameMetricsAvailableListenerCounter)
    }

    @Test
    fun `collector ignores activities if not started`() {
        val collector = fixture.getSut(context)

        assertEquals(0, fixture.removeOnFrameMetricsAvailableListenerCounter)
        assertEquals(0, fixture.addOnFrameMetricsAvailableListenerCounter)
        collector.onActivityStarted(fixture.activity)
        collector.onActivityStopped(fixture.activity)
        assertEquals(0, fixture.removeOnFrameMetricsAvailableListenerCounter)
        assertEquals(0, fixture.addOnFrameMetricsAvailableListenerCounter)
    }

    @Test
    fun `startCollection calls addOnFrameMetricsAvailableListener if an activity is already started`() {
        val collector = fixture.getSut(context)

        collector.onActivityStarted(fixture.activity)
        assertEquals(0, fixture.addOnFrameMetricsAvailableListenerCounter)
        collector.startCollection(mock())
        assertEquals(1, fixture.addOnFrameMetricsAvailableListenerCounter)
    }

    @Test
    fun `stopCollection calls removeOnFrameMetricsAvailableListener even if an activity is still started`() {
        val collector = fixture.getSut(context)
        val id = collector.startCollection(mock())
        collector.onActivityStarted(fixture.activity)

        assertEquals(0, fixture.removeOnFrameMetricsAvailableListenerCounter)
        collector.stopCollection(id)
        assertEquals(1, fixture.removeOnFrameMetricsAvailableListenerCounter)
    }

    @Test
    fun `OnFrameMetricsAvailableListener is called once per activity`() {
        val collector = fixture.getSut(context)
        collector.startCollection(mock())

        assertEquals(0, fixture.addOnFrameMetricsAvailableListenerCounter)
        assertEquals(0, fixture.removeOnFrameMetricsAvailableListenerCounter)

        collector.onActivityStarted(fixture.activity)
        collector.onActivityStarted(fixture.activity)

        collector.onActivityStopped(fixture.activity)
        collector.onActivityStopped(fixture.activity)

        assertEquals(1, fixture.addOnFrameMetricsAvailableListenerCounter)
        assertEquals(1, fixture.removeOnFrameMetricsAvailableListenerCounter)
    }

    @Test
    fun `stopCollection works only after startCollection`() {
        val collector = fixture.getSut(context)
        collector.startCollection(mock())
        collector.onActivityStarted(fixture.activity)
        collector.stopCollection("testId")
        assertEquals(0, fixture.removeOnFrameMetricsAvailableListenerCounter)
    }

    @Test
    fun `collector tracks multiple activities`() {
        val collector = fixture.getSut(context)
        collector.startCollection(mock())
        collector.onActivityStarted(fixture.activity)
        collector.onActivityStarted(fixture.activity2)
        assertEquals(2, fixture.addOnFrameMetricsAvailableListenerCounter)
        collector.onActivityStopped(fixture.activity)
        collector.onActivityStopped(fixture.activity2)
        assertEquals(2, fixture.removeOnFrameMetricsAvailableListenerCounter)
    }

    @Test
    fun `collector tracks multiple collections`() {
        val collector = fixture.getSut(context)
        val id1 = collector.startCollection(mock())
        val id2 = collector.startCollection(mock())
        collector.onActivityStarted(fixture.activity)
        assertEquals(1, fixture.addOnFrameMetricsAvailableListenerCounter)
        collector.stopCollection(id1)
        assertEquals(0, fixture.removeOnFrameMetricsAvailableListenerCounter)
        collector.stopCollection(id2)
        assertEquals(1, fixture.removeOnFrameMetricsAvailableListenerCounter)
    }

    @Test
    fun `collector removes current window only when last activity stops`() {
        val collector = fixture.getSut(context)
        val id1 = collector.startCollection(mock())
        collector.onActivityStarted(fixture.activity)
        collector.onActivityStarted(fixture.activity2)

        // Stopping collecting data doesn't clear current tracked window reference
        collector.stopCollection(id1)
        assertNotNull(collector.getProperty<WeakReference<Window>?>("currentWindow"))

        // Stopping first activity doesn't clear current tracked window reference
        collector.onActivityStopped(fixture.activity)
        assertNotNull(collector.getProperty<WeakReference<Window>?>("currentWindow"))

        // Stopping last activity clears current tracked window reference
        collector.onActivityStopped(fixture.activity2)
        assertNull(collector.getProperty<WeakReference<Window>?>("currentWindow"))
    }
}
