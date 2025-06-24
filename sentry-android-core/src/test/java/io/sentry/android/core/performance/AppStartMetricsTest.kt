package io.sentry.android.core.performance

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.DateUtils
import io.sentry.IContinuousProfiler
import io.sentry.ITransactionProfiler
import io.sentry.SentryNanotimeDate
import io.sentry.android.core.CurrentActivityHolder
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.android.core.SentryShadowProcess
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.N], shadows = [SentryShadowProcess::class])
class AppStartMetricsTest {
  @Before
  fun setup() {
    AppStartMetrics.getInstance().clear()
    SentryShadowProcess.setStartUptimeMillis(42)
    AppStartMetrics.getInstance().isAppLaunchedInForeground = true
  }

  @Test
  fun `getInstance returns a singleton`() {
    assertSame(AppStartMetrics.getInstance(), AppStartMetrics.getInstance())
  }

  @Test
  fun `metrics are properly cleared`() {
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.start()
    metrics.sdkInitTimeSpan.start()
    metrics.appStartType = AppStartMetrics.AppStartType.WARM
    metrics.applicationOnCreateTimeSpan.start()
    metrics.addActivityLifecycleTimeSpans(ActivityLifecycleTimeSpan())
    AppStartMetrics.onApplicationCreate(mock<Application>())
    AppStartMetrics.onContentProviderCreate(mock<ContentProvider>())
    metrics.appStartProfiler = mock()
    metrics.appStartContinuousProfiler = mock()
    metrics.appStartSamplingDecision = mock()

    metrics.clear()

    assertTrue(metrics.appStartTimeSpan.hasNotStarted())
    assertTrue(metrics.sdkInitTimeSpan.hasNotStarted())
    assertTrue(metrics.applicationOnCreateTimeSpan.hasNotStarted())
    assertEquals(AppStartMetrics.AppStartType.UNKNOWN, metrics.appStartType)

    assertTrue(metrics.activityLifecycleTimeSpans.isEmpty())
    assertTrue(metrics.contentProviderOnCreateTimeSpans.isEmpty())
    assertNull(metrics.appStartProfiler)
    assertNull(metrics.appStartContinuousProfiler)
    assertNull(metrics.appStartSamplingDecision)
  }

  @Test
  fun `if perf-2 is enabled and app start time span is started, appStartTimeSpanWithFallback returns it`() {
    val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
    AppStartMetrics.getInstance().appStartType = AppStartMetrics.AppStartType.WARM
    appStartTimeSpan.start()

    val options = SentryAndroidOptions().apply { isEnablePerformanceV2 = true }

    val timeSpan = AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options)
    assertSame(appStartTimeSpan, timeSpan)
  }

  @Test
  fun `if perf-2 is disabled but app start time span has started, appStartTimeSpanWithFallback returns the sdk init span instead`() {
    val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
    AppStartMetrics.getInstance().appStartType = AppStartMetrics.AppStartType.COLD
    AppStartMetrics.getInstance().sdkInitTimeSpan.apply {
      setStartedAt(123)
      setStoppedAt(456)
    }
    appStartTimeSpan.setStartedAt(123)

    val options = SentryAndroidOptions().apply { isEnablePerformanceV2 = false }

    val timeSpan = AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options)
    val sdkInitSpan = AppStartMetrics.getInstance().sdkInitTimeSpan
    assertSame(sdkInitSpan, timeSpan)
  }

  @Test
  fun `if perf-2 is enabled but app start time span has not started, appStartTimeSpanWithFallback returns the sdk init span instead`() {
    AppStartMetrics.getInstance().appStartType = AppStartMetrics.AppStartType.COLD
    AppStartMetrics.getInstance().sdkInitTimeSpan.apply {
      setStartedAt(123)
      setStoppedAt(456)
    }

    val options = SentryAndroidOptions().apply { isEnablePerformanceV2 = true }

    val timeSpan = AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options)
    val sdkInitSpan = AppStartMetrics.getInstance().sdkInitTimeSpan
    assertSame(sdkInitSpan, timeSpan)
  }

  @Test
  fun `class load time is set`() {
    assertNotEquals(0, AppStartMetrics.getInstance().classLoadedUptimeMs)
  }

  @Test
  fun `if app is launched in background, appStartTimeSpanWithFallback returns an empty span`() {
    AppStartMetrics.getInstance().isAppLaunchedInForeground = false
    AppStartMetrics.getInstance().appStartType = AppStartMetrics.AppStartType.COLD

    val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
    appStartTimeSpan.start()
    assertTrue(appStartTimeSpan.hasStarted())
    AppStartMetrics.getInstance().onActivityCreated(mock(), mock())
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val options = SentryAndroidOptions().apply { isEnablePerformanceV2 = false }

    val timeSpan = AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options)
    assertFalse(timeSpan.hasStarted())
  }

  @Test
  fun `if app is launched in background, but an activity launches later, a new warm start is reported with correct timings`() {
    val metrics = AppStartMetrics.getInstance()
    metrics.registerLifecycleCallbacks(mock<Application>())

    metrics.contentProviderOnCreateTimeSpans.add(
      TimeSpan().apply {
        description = "ExampleContentProvider"
        setStartedAt(1)
        setStoppedAt(2)
      }
    )

    metrics.applicationOnCreateTimeSpan.apply {
      setStartedAt(3)
      setStoppedAt(4)
    }

    // when the looper runs
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    // but no activity creation happened
    // then the app wasn't launched in foreground and nothing should be sent
    assertFalse(metrics.isAppLaunchedInForeground)
    assertFalse(metrics.shouldSendStartMeasurements())

    val now = TimeUnit.MINUTES.toMillis(2) + 1234567
    SystemClock.setCurrentTimeMillis(now)

    // once an activity launches
    AppStartMetrics.getInstance().onActivityCreated(mock(), null)

    // then it should restart the timespan
    assertTrue(metrics.isAppLaunchedInForeground)
    assertTrue(metrics.shouldSendStartMeasurements())
    assertTrue(metrics.appStartTimeSpan.hasStarted())
    assertEquals(now, metrics.appStartTimeSpan.startUptimeMs)
    assertFalse(metrics.applicationOnCreateTimeSpan.hasStarted())
    assertTrue(metrics.contentProviderOnCreateTimeSpans.isEmpty())
  }

  @Test
  fun `if app is launched in background, the first created activity assumes a warm start`() {
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.start()
    metrics.sdkInitTimeSpan.start()
    metrics.registerLifecycleCallbacks(mock<Application>())

    // when the handler callback is executed and no activity was launched
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    // isAppLaunchedInForeground should be false
    assertFalse(metrics.isAppLaunchedInForeground)

    // but when the first activity launches
    metrics.onActivityCreated(mock<Activity>(), null)

    // then a warm start should be set
    assertTrue(metrics.isAppLaunchedInForeground)
    assertEquals(AppStartMetrics.AppStartType.WARM, metrics.appStartType)
  }

  @Test
  fun `if app start span is at most 1 minute, appStartTimeSpanWithFallback returns the app start span`() {
    val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
    appStartTimeSpan.start()
    appStartTimeSpan.stop()
    appStartTimeSpan.setStartedAt(1)
    appStartTimeSpan.setStoppedAt(TimeUnit.MINUTES.toMillis(1) + 1)
    assertTrue(appStartTimeSpan.hasStarted())
    AppStartMetrics.getInstance().onActivityCreated(mock(), mock())

    val options = SentryAndroidOptions().apply { isEnablePerformanceV2 = true }

    val timeSpan = AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options)
    assertTrue(timeSpan.hasStarted())
    assertSame(appStartTimeSpan, timeSpan)
  }

  @Test
  fun `if activity is never started, returns an empty span`() {
    AppStartMetrics.getInstance().registerLifecycleCallbacks(mock())
    val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
    appStartTimeSpan.setStartedAt(1)
    assertTrue(appStartTimeSpan.hasStarted())
    // Job on main thread checks if activity was launched
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val timeSpan =
      AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(SentryAndroidOptions())
    assertFalse(timeSpan.hasStarted())
  }

  @Test
  fun `if activity is never started, stops app start profiler if running`() {
    val profiler = mock<ITransactionProfiler>()
    whenever(profiler.isRunning).thenReturn(true)
    AppStartMetrics.getInstance().appStartProfiler = profiler

    AppStartMetrics.getInstance().registerLifecycleCallbacks(mock())
    // Job on main thread checks if activity was launched
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    verify(profiler).close()
  }

  @Test
  fun `if activity is never started, stops app start continuous profiler if running`() {
    val profiler = mock<IContinuousProfiler>()
    whenever(profiler.isRunning).thenReturn(true)
    AppStartMetrics.getInstance().appStartContinuousProfiler = profiler

    AppStartMetrics.getInstance().registerLifecycleCallbacks(mock())
    // Job on main thread checks if activity was launched
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    verify(profiler).close(eq(true))
  }

  @Test
  fun `if activity is started, does not stop app start profiler if running`() {
    val profiler = mock<ITransactionProfiler>()
    whenever(profiler.isRunning).thenReturn(true)
    AppStartMetrics.getInstance().appStartProfiler = profiler
    AppStartMetrics.getInstance().onActivityCreated(mock(), mock())

    AppStartMetrics.getInstance().registerLifecycleCallbacks(mock())
    // Job on main thread checks if activity was launched
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    verify(profiler, never()).close()
  }

  @Test
  fun `if activity is started, does not stop app start continuous profiler if running`() {
    val profiler = mock<IContinuousProfiler>()
    whenever(profiler.isRunning).thenReturn(true)
    AppStartMetrics.getInstance().appStartContinuousProfiler = profiler
    AppStartMetrics.getInstance().onActivityCreated(mock(), mock())

    AppStartMetrics.getInstance().registerLifecycleCallbacks(mock())
    // Job on main thread checks if activity was launched
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    verify(profiler, never()).close(any())
  }

  @Test
  fun `if app start span is longer than 1 minute, appStartTimeSpanWithFallback returns an empty span`() {
    val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
    appStartTimeSpan.start()
    appStartTimeSpan.stop()
    appStartTimeSpan.setStartedAt(1)
    appStartTimeSpan.setStoppedAt(TimeUnit.MINUTES.toMillis(1) + 2)
    assertTrue(appStartTimeSpan.hasStarted())
    AppStartMetrics.getInstance().onActivityCreated(mock(), mock())

    val options = SentryAndroidOptions().apply { isEnablePerformanceV2 = true }

    val timeSpan = AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options)
    assertFalse(timeSpan.hasStarted())
  }

  @Test
  fun `when multiple registerApplicationForegroundCheck, only one callback is registered to application`() {
    val application = mock<Application>()
    AppStartMetrics.getInstance().registerLifecycleCallbacks(application)
    AppStartMetrics.getInstance().registerLifecycleCallbacks(application)
    verify(application, times(1))
      .registerActivityLifecycleCallbacks(eq(AppStartMetrics.getInstance()))
  }

  @Test
  fun `when registerApplicationForegroundCheck, a callback is registered to application`() {
    val application = mock<Application>()
    AppStartMetrics.getInstance().registerLifecycleCallbacks(application)
    verify(application).registerActivityLifecycleCallbacks(eq(AppStartMetrics.getInstance()))
  }

  @Test
  fun `registerApplicationForegroundCheck set foreground state to false if no activity is running`() {
    val application = mock<Application>()
    AppStartMetrics.getInstance().isAppLaunchedInForeground = true
    AppStartMetrics.getInstance().registerLifecycleCallbacks(application)
    assertTrue(AppStartMetrics.getInstance().isAppLaunchedInForeground)
    // Main thread performs the check and sets the flag to false if no activity was created
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertFalse(AppStartMetrics.getInstance().isAppLaunchedInForeground)
  }

  @Test
  fun `registerApplicationForegroundCheck keeps foreground state to true if an activity is running`() {
    val application = mock<Application>()
    AppStartMetrics.getInstance().isAppLaunchedInForeground = true
    AppStartMetrics.getInstance().registerLifecycleCallbacks(application)
    assertTrue(AppStartMetrics.getInstance().isAppLaunchedInForeground)
    // An activity was created
    AppStartMetrics.getInstance().onActivityCreated(mock(), null)
    // Main thread performs the check and keeps the flag to true
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertTrue(AppStartMetrics.getInstance().isAppLaunchedInForeground)
  }

  @Test
  fun `isColdStartValid is false if app launched in more than 1 minute`() {
    val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
    appStartTimeSpan.start()
    appStartTimeSpan.stop()
    appStartTimeSpan.setStartedAt(1)
    appStartTimeSpan.setStoppedAt(TimeUnit.MINUTES.toMillis(1) + 2)
    AppStartMetrics.getInstance().onActivityCreated(mock(), mock())
  }

  @Test
  fun `onAppStartSpansSent set measurement flag and clear internal lists`() {
    val appStartMetrics = AppStartMetrics.getInstance()
    appStartMetrics.addActivityLifecycleTimeSpans(mock())
    appStartMetrics.contentProviderOnCreateTimeSpans.add(mock())
    assertTrue(appStartMetrics.shouldSendStartMeasurements())
    appStartMetrics.onAppStartSpansSent()
    assertTrue(appStartMetrics.activityLifecycleTimeSpans.isEmpty())
    assertTrue(appStartMetrics.contentProviderOnCreateTimeSpans.isEmpty())
    assertFalse(appStartMetrics.shouldSendStartMeasurements())
  }

  @Test
  fun `a warm start gets reported after a cold start`() {
    val appStartMetrics = AppStartMetrics.getInstance()

    // when the first activity launches and gets destroyed
    val activity0 = mock<Activity>()
    whenever(activity0.isChangingConfigurations).thenReturn(false)
    appStartMetrics.onActivityCreated(activity0, null)

    // then the app start type should be cold and measurements should be sent
    assertEquals(AppStartMetrics.AppStartType.COLD, appStartMetrics.appStartType)
    assertTrue(appStartMetrics.shouldSendStartMeasurements())

    // when the activity gets destroyed
    appStartMetrics.onAppStartSpansSent()
    assertFalse(appStartMetrics.shouldSendStartMeasurements())

    appStartMetrics.onActivityDestroyed(activity0)

    // then it should reset sending the measurements for the next warm activity
    appStartMetrics.onActivityCreated(mock<Activity>(), mock<Bundle>())
    assertEquals(AppStartMetrics.AppStartType.WARM, appStartMetrics.appStartType)
    assertTrue(appStartMetrics.shouldSendStartMeasurements())
  }

  @Test
  fun `provider sets both appstart and sdk init start + end times`() {
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.start()
    metrics.sdkInitTimeSpan.start()

    assertFalse(metrics.appStartTimeSpan.hasStopped())
    assertFalse(metrics.sdkInitTimeSpan.hasStopped())

    metrics.onFirstFrameDrawn()

    assertTrue(metrics.appStartTimeSpan.hasStopped())
    assertTrue(metrics.sdkInitTimeSpan.hasStopped())
  }

  @Test
  fun `Sets app launch type to cold`() {
    val metrics = AppStartMetrics.getInstance()
    assertEquals(AppStartMetrics.AppStartType.UNKNOWN, AppStartMetrics.getInstance().appStartType)

    val app = mock<Application>()
    metrics.registerLifecycleCallbacks(app)
    metrics.onActivityCreated(mock<Activity>(), null)

    // then the app start is considered cold
    assertEquals(AppStartMetrics.AppStartType.COLD, AppStartMetrics.getInstance().appStartType)

    // when any subsequent activity launches
    metrics.onActivityCreated(mock<Activity>(), mock<Bundle>())

    // then the app start is still considered cold
    assertEquals(AppStartMetrics.AppStartType.COLD, AppStartMetrics.getInstance().appStartType)
  }

  @Test
  fun `Sets app launch type to warm if process init was too long ago`() {
    val metrics = AppStartMetrics.getInstance()
    assertEquals(AppStartMetrics.AppStartType.UNKNOWN, AppStartMetrics.getInstance().appStartType)
    val app = mock<Application>()
    metrics.registerLifecycleCallbacks(app)

    // when an activity is created later with a null bundle
    SystemClock.setCurrentTimeMillis(TimeUnit.MINUTES.toMillis(2))
    metrics.onActivityCreated(mock<Activity>(), null)

    // then the app start is considered warm
    assertEquals(AppStartMetrics.AppStartType.WARM, AppStartMetrics.getInstance().appStartType)
  }

  @Test
  fun `Sets app launch type to warm`() {
    val metrics = AppStartMetrics.getInstance()
    assertEquals(AppStartMetrics.AppStartType.UNKNOWN, AppStartMetrics.getInstance().appStartType)

    val app = mock<Application>()
    metrics.registerLifecycleCallbacks(app)
    metrics.onActivityCreated(mock<Activity>(), mock<Bundle>())

    // then the app start is considered warm
    assertEquals(AppStartMetrics.AppStartType.WARM, AppStartMetrics.getInstance().appStartType)

    // when any subsequent activity launches
    metrics.onActivityCreated(mock<Activity>(), null)

    // then the app start is still considered warm
    assertEquals(AppStartMetrics.AppStartType.WARM, AppStartMetrics.getInstance().appStartType)
  }

  @Test
  fun `createProcessInitSpan creates a span`() {
    val appStartMetrics = AppStartMetrics.getInstance()
    val startDate = SentryNanotimeDate(Date(1), 1000000)
    appStartMetrics.classLoadedUptimeMs = 10
    val startMillis = DateUtils.nanosToMillis(startDate.nanoTimestamp().toDouble()).toLong()
    appStartMetrics.appStartTimeSpan.setStartedAt(1)
    appStartMetrics.appStartTimeSpan.setStartUnixTimeMs(startMillis)
    val span = appStartMetrics.createProcessInitSpan()

    assertEquals("Process Initialization", span.description)
    // Start timestampMs is taken by appStartSpan
    assertEquals(startMillis, span.startTimestampMs)
    // Start uptime is taken by appStartSpan and stop uptime is class loaded uptime: 10 - 1
    assertEquals(9, span.durationMs)
    // Class loaded uptimeMs is 10 ms, and process init span should finish at the same ms
    assertEquals(10, span.projectedStopTimestampMs)
  }

  @Test
  fun `when an activity is created the activity holder provides it`() {
    val metrics = AppStartMetrics.getInstance()
    val activity = mock<Activity>()

    metrics.onActivityCreated(activity, null)
    assertEquals(activity, CurrentActivityHolder.getInstance().activity)
  }

  @Test
  fun `when there is no active activity the holder does not provide an outdated one`() {
    val metrics = AppStartMetrics.getInstance()
    val activity = mock<Activity>()

    metrics.onActivityCreated(activity, null)
    metrics.onActivityDestroyed(activity)

    assertNull(CurrentActivityHolder.getInstance().activity)
  }

  @Test
  fun `when a second activity is started it gets the current one`() {
    val metrics = AppStartMetrics.getInstance()
    val firstActivity = mock<Activity>()

    metrics.onActivityCreated(firstActivity, null)
    metrics.onActivityStarted(firstActivity)
    metrics.onActivityResumed(firstActivity)

    val secondActivity = mock<Activity>()
    metrics.onActivityCreated(secondActivity, null)
    metrics.onActivityStarted(secondActivity)

    assertEquals(secondActivity, CurrentActivityHolder.getInstance().activity)
  }

  @Test
  fun `destroying an old activity keeps the current one`() {
    val metrics = AppStartMetrics.getInstance()
    val firstActivity = mock<Activity>()

    metrics.onActivityCreated(firstActivity, null)
    metrics.onActivityStarted(firstActivity)
    metrics.onActivityResumed(firstActivity)

    val secondActivity = mock<Activity>()
    metrics.onActivityCreated(secondActivity, null)
    metrics.onActivityStarted(secondActivity)

    metrics.onActivityPaused(firstActivity)
    metrics.onActivityStopped(firstActivity)
    metrics.onActivityDestroyed(firstActivity)

    assertEquals(secondActivity, CurrentActivityHolder.getInstance().activity)
  }
}
