package io.sentry.android.core.internal.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Display
import android.view.FrameMetrics
import android.view.Window
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.SentryOptions
import io.sentry.android.core.BuildInfoProvider
import io.sentry.test.getCtor
import io.sentry.test.getProperty
import io.sentry.test.injectForField
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
class SentryFrameMetricsCollectorTest {
  private lateinit var context: Context

  private val className = "io.sentry.android.core.internal.util.SentryFrameMetricsCollector"
  private val ctorTypes =
    arrayOf(Context::class.java, SentryOptions::class.java, BuildInfoProvider::class.java)
  private val fixture = Fixture()

  private class Fixture {
    private val mockDsn = "http://key@localhost/proj"
    val buildInfo =
      mock<BuildInfoProvider> { whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N) }
    val mockLogger = mock<ILogger>()
    val options =
      spy(SentryOptions()).apply {
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
    val windowFrameMetricsManager =
      object : SentryFrameMetricsCollector.WindowFrameMetricsManager {
        override fun addOnFrameMetricsAvailableListener(
          window: Window,
          frameMetricsAvailableListener: Window.OnFrameMetricsAvailableListener?,
          handler: Handler?,
        ) {
          addOnFrameMetricsAvailableListenerCounter++
        }

        override fun removeOnFrameMetricsAvailableListener(
          window: Window,
          frameMetricsAvailableListener: Window.OnFrameMetricsAvailableListener?,
        ) {
          removeOnFrameMetricsAvailableListenerCounter++
        }
      }

    fun getSut(
      context: Context,
      buildInfoProvider: BuildInfoProvider = buildInfo,
    ): SentryFrameMetricsCollector {
      whenever(activity.window).thenReturn(window)
      whenever(activity2.window).thenReturn(window2)
      addOnFrameMetricsAvailableListenerCounter = 0
      removeOnFrameMetricsAvailableListenerCounter = 0
      return SentryFrameMetricsCollector(
        context,
        options,
        buildInfoProvider,
        windowFrameMetricsManager,
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
    val buildInfo =
      mock<BuildInfoProvider> { whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.M) }
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

  @Test
  fun `collector accesses choreographer instance on creation on main thread`() {
    val collector = fixture.getSut(context)
    val field: Field? = collector.getProperty("choreographerLastFrameTimeField")
    var choreographer: Choreographer? = collector.getProperty("choreographer")
    // Choreographer instance is accessed on main thread, but the field accessor happens in whatever
    // thread created the collector
    assertNotNull(field)
    assertNull(choreographer)
    // Execute all posted tasks
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    choreographer = collector.getProperty("choreographer")
    assertNotNull(choreographer)
  }

  @Test
  fun `collector reads frame start from choreographer field under version O`() {
    val collector = fixture.getSut(context)
    // Execute all posted tasks
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    val listener =
      collector.getProperty<Window.OnFrameMetricsAvailableListener>("frameMetricsAvailableListener")
    val choreographer = collector.getProperty<Choreographer>("choreographer")
    choreographer.injectForField("mLastFrameTimeNanos", 100)
    val frameMetrics = createMockFrameMetrics()

    var timesCalled = 0
    collector.startCollection {
      frameStartNanos,
      frameEndNanos,
      durationNanos,
      delayNanos,
      isSlow,
      isFrozen,
      refreshRate ->
      // The frame end is 100 (Choreographer.mLastFrameTimeNanos) plus frame duration
      assertEquals(100 + durationNanos, frameEndNanos)
      timesCalled++
    }
    listener.onFrameMetricsAvailable(createMockWindow(), frameMetrics, 0)
    // Assert the callback was called
    assertEquals(1, timesCalled)
  }

  @Test
  fun `collector reads frame start from frameMetrics object on version O+`() {
    val buildInfo =
      mock<BuildInfoProvider> { whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.O) }
    val collector = fixture.getSut(context, buildInfo)
    val listener =
      collector.getProperty<Window.OnFrameMetricsAvailableListener>("frameMetricsAvailableListener")
    val frameMetrics = createMockFrameMetrics()
    // We don't inject the choreographer field

    var timesCalled = 0
    collector.startCollection {
      frameStartNanos,
      frameEndNanos,
      durationNanos,
      delayNanos,
      isSlow,
      isFrozen,
      refreshRate ->
      assertEquals(50 + durationNanos, frameEndNanos)
      timesCalled++
    }
    listener.onFrameMetricsAvailable(createMockWindow(), frameMetrics, 0)
    // Assert the callback was called
    assertEquals(1, timesCalled)
  }

  @Test
  fun `collector reads only cpu main thread frame duration`() {
    val buildInfo =
      mock<BuildInfoProvider> { whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.O) }
    val collector = fixture.getSut(context, buildInfo)
    val listener =
      collector.getProperty<Window.OnFrameMetricsAvailableListener>("frameMetricsAvailableListener")
    // FrameMetrics with cpu time of 21 nanoseconds and TOTAL_DURATION of 60 nanoseconds
    val frameMetrics = createMockFrameMetrics()

    var timesCalled = 0
    collector.startCollection {
      frameStartNanos,
      frameEndNanos,
      durationNanos,
      delayNanos,
      isSlow,
      isFrozen,
      refreshRate ->
      assertEquals(21, durationNanos)
      timesCalled++
    }
    listener.onFrameMetricsAvailable(createMockWindow(), frameMetrics, 0)
    // Assert the callback was called
    assertEquals(1, timesCalled)
  }

  @Test
  fun `collector adjusts frames start with previous end`() {
    val buildInfo =
      mock<BuildInfoProvider> { whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.O) }
    val collector = fixture.getSut(context, buildInfo)
    val listener =
      collector.getProperty<Window.OnFrameMetricsAvailableListener>("frameMetricsAvailableListener")
    val frameMetrics = createMockFrameMetrics()
    whenever(frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)).thenReturn(50)
    var previousEnd = 0L
    var timesCalled = 0
    collector.startCollection {
      frameStartNanos,
      frameEndNanos,
      durationNanos,
      delayNanos,
      isSlow,
      isFrozen,
      refreshRate ->
      // The second time the listener is called, the frame start is shifted to be equal to the
      // previous frame end
      if (timesCalled > 0) {
        assertEquals(previousEnd + durationNanos, frameEndNanos)
      }
      previousEnd = frameEndNanos
      timesCalled++
    }
    listener.onFrameMetricsAvailable(createMockWindow(), frameMetrics, 0)
    listener.onFrameMetricsAvailable(createMockWindow(), frameMetrics, 0)
    // Assert the callback was called two times
    assertEquals(2, timesCalled)
  }

  @Test
  fun `collector properly reports slow and frozen flags`() {
    val buildInfo =
      mock<BuildInfoProvider> { whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.O) }
    val collector = fixture.getSut(context, buildInfo)
    val listener =
      collector.getProperty<Window.OnFrameMetricsAvailableListener>("frameMetricsAvailableListener")

    var timesCalled = 0
    var lastIsSlow = false
    var lastIsFrozen = false

    // when a frame takes less than 16ms, it's not considered slow or frozen
    collector.startCollection { _, _, _, _, isSlow, isFrozen, _ ->
      lastIsSlow = isSlow
      lastIsFrozen = isFrozen
      timesCalled++
    }
    listener.onFrameMetricsAvailable(createMockWindow(), createMockFrameMetrics(), 0)
    assertFalse(lastIsSlow)
    assertFalse(lastIsFrozen)

    // when a frame takes more than 16ms, it's considered slow but not frozen
    listener.onFrameMetricsAvailable(
      createMockWindow(),
      createMockFrameMetrics(extraCpuDurationNanos = TimeUnit.MILLISECONDS.toNanos(100)),
      0,
    )
    assertTrue(lastIsSlow)
    assertFalse(lastIsFrozen)

    // when a frame takes more than 700ms, it's considered slow and frozen
    listener.onFrameMetricsAvailable(
      createMockWindow(),
      createMockFrameMetrics(extraCpuDurationNanos = TimeUnit.MILLISECONDS.toNanos(1000)),
      0,
    )
    assertTrue(lastIsSlow)
    assertTrue(lastIsFrozen)

    // Assert the callbacks were called
    assertEquals(3, timesCalled)
  }

  @Test
  fun `collector properly reports frame delay`() {
    val buildInfo =
      mock<BuildInfoProvider> { whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.O) }
    val collector = fixture.getSut(context, buildInfo)
    val listener =
      collector.getProperty<Window.OnFrameMetricsAvailableListener>("frameMetricsAvailableListener")

    var lastDelay = 0L

    // when a frame takes less than 16ms, it's not considered slow or frozen
    collector.startCollection { _, _, _, delayNanos, _, _, _ -> lastDelay = delayNanos }
    // at 60hz, when the total duration is 10ms, the delay is 0
    listener.onFrameMetricsAvailable(
      createMockWindow(),
      createMockFrameMetrics(
        unknownDelayNanos = 0,
        animationNanos = 0,
        inputHandlingNanos = 0,
        layoutMeasureNanos = 0,
        drawNanos = 0,
        syncNanos = 0,
        extraCpuDurationNanos = TimeUnit.MILLISECONDS.toNanos(16),
      ),
      0,
    )
    assertEquals(0, lastDelay)

    // at 60hz, when the total duration is 20ms, the delay is considered ~4ms
    listener.onFrameMetricsAvailable(
      createMockWindow(),
      createMockFrameMetrics(
        unknownDelayNanos = 0,
        animationNanos = 0,
        inputHandlingNanos = 0,
        layoutMeasureNanos = 0,
        drawNanos = 0,
        syncNanos = 0,
        extraCpuDurationNanos = TimeUnit.MILLISECONDS.toNanos(20),
      ),
      0,
    )
    assertEquals(
      // 20ms - 1/60 (~16.6ms) = 4ms
      TimeUnit.MILLISECONDS.toNanos(20) - (TimeUnit.SECONDS.toNanos(1) / 60.0f).toLong(),
      lastDelay,
    )

    // at 120hz, when the total duration is 20ms, the delay is considered ~8ms
    listener.onFrameMetricsAvailable(
      createMockWindow(120.0f),
      createMockFrameMetrics(
        unknownDelayNanos = 0,
        animationNanos = 0,
        inputHandlingNanos = 0,
        layoutMeasureNanos = 0,
        drawNanos = 0,
        syncNanos = 0,
        extraCpuDurationNanos = TimeUnit.MILLISECONDS.toNanos(20),
      ),
      0,
    )
    assertEquals(
      // 20ms - 1/120 (~8.33ms) = 8ms
      TimeUnit.MILLISECONDS.toNanos(20) - (TimeUnit.SECONDS.toNanos(1) / 120.0f).toLong(),
      lastDelay,
    )
  }

  private fun createMockWindow(refreshRate: Float = 60F): Window {
    val mockWindow = mock<Window>()
    val mockDisplay = mock<Display>()
    val mockWindowManager = mock<WindowManager>()
    whenever(mockWindow.windowManager).thenReturn(mockWindowManager)
    whenever(mockWindowManager.defaultDisplay).thenReturn(mockDisplay)
    whenever(mockDisplay.refreshRate).thenReturn(refreshRate)
    return mockWindow
  }

  /**
   * FrameMetrics with default cpu time of 21 nanoseconds and INTENDED_VSYNC_TIMESTAMP of 50
   * nanoseconds
   */
  private fun createMockFrameMetrics(
    unknownDelayNanos: Long = 1,
    inputHandlingNanos: Long = 2,
    animationNanos: Long = 3,
    layoutMeasureNanos: Long = 4,
    drawNanos: Long = 5,
    syncNanos: Long = 6,
    extraCpuDurationNanos: Long = 0,
    totalDurationNanos: Long = 60,
  ): FrameMetrics {
    val frameMetrics = mock<FrameMetrics>()
    whenever(frameMetrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION))
      .thenReturn(unknownDelayNanos + extraCpuDurationNanos)
    whenever(frameMetrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION))
      .thenReturn(inputHandlingNanos)
    whenever(frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION)).thenReturn(animationNanos)
    whenever(frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))
      .thenReturn(layoutMeasureNanos)
    whenever(frameMetrics.getMetric(FrameMetrics.DRAW_DURATION)).thenReturn(drawNanos)
    whenever(frameMetrics.getMetric(FrameMetrics.SYNC_DURATION)).thenReturn(syncNanos)
    whenever(frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)).thenReturn(totalDurationNanos)
    whenever(frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)).thenReturn(50)
    return frameMetrics
  }
}
