package io.sentry.android.core.performance

import android.app.Activity
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.app.ApplicationStartInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.android.core.SentryShadowActivityManager
import io.sentry.android.core.SentryShadowProcess
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(
  sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM],
  shadows = [SentryShadowProcess::class, SentryShadowActivityManager::class],
)
class AppStartMetricsTestApi35 {
  @Before
  fun setup() {
    AppStartMetrics.getInstance().clear()
    SentryShadowProcess.setStartUptimeMillis(42)
    SentryShadowProcess.setStartElapsedRealtime(42)
    SentryShadowActivityManager.reset()
    AppStartMetrics.getInstance().setClassLoadedUptimeMs(42)
    AppStartMetrics.getInstance().isAppLaunchedInForeground = true
  }

  @Test
  fun `detects cold start using ApplicationStartInfo on API 35`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))

    val app = ApplicationProvider.getApplicationContext<Application>()
    AppStartMetrics.getInstance().registerLifecycleCallbacks(app)

    assertEquals(AppStartMetrics.AppStartType.COLD, AppStartMetrics.getInstance().appStartType)
  }

  @Test
  fun `known ApplicationStartInfo type without listener does not schedule headless check`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    val metrics = AppStartMetrics.getInstance()

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)
    waitForMainLooperIdle()

    assertEquals(AppStartMetrics.AppStartType.COLD, metrics.appStartType)
    assertEquals(-1, metrics.firstIdle)
  }

  @Test
  fun `detects warm start using ApplicationStartInfo on API 35`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_WARM)
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))

    val app = ApplicationProvider.getApplicationContext<Application>()
    AppStartMetrics.getInstance().registerLifecycleCallbacks(app)

    assertEquals(AppStartMetrics.AppStartType.WARM, AppStartMetrics.getInstance().appStartType)
  }

  @Test
  fun `does not set app start type when ApplicationStartInfo list is invalid`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState)
      .thenReturn(ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_WARM)
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))

    val metrics = AppStartMetrics.getInstance()

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)

    assertEquals(AppStartMetrics.AppStartType.UNKNOWN, metrics.appStartType)
  }

  @Test
  fun `does not set app start type when ApplicationStartInfo list is empty`() {
    SentryShadowActivityManager.setHistoricalProcessStartReasons(emptyList())
    val metrics = AppStartMetrics.getInstance()

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)

    assertEquals(AppStartMetrics.AppStartType.UNKNOWN, metrics.appStartType)
  }

  @Test
  fun `headless app start keeps COLD appStartType from ApplicationStartInfo`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.startupTimestamps).thenReturn(emptyMap())
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    SentryShadowActivityManager.setImportance(RunningAppProcessInfo.IMPORTANCE_CACHED)
    val listenerCalls = AtomicInteger()
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.setStartedAt(100)
    metrics.setHeadlessAppStartListener { listenerCalls.incrementAndGet() }

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)
    waitForMainLooperIdle()

    assertEquals(AppStartMetrics.AppStartType.COLD, metrics.appStartType)
    assertFalse(metrics.isAppLaunchedInForeground)
    assertEquals(1, listenerCalls.get())
  }

  @Test
  fun `known ApplicationStartInfo type with listener handles headless app start`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_WARM)
    whenever(mockStartInfo.startupTimestamps).thenReturn(emptyMap())
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    SentryShadowActivityManager.setImportance(RunningAppProcessInfo.IMPORTANCE_CACHED)
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.setStartedAt(100)
    metrics.setClassLoadedUptimeMs(200)
    metrics.setHeadlessAppStartListener {}

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)
    waitForMainLooperIdle()

    assertEquals(AppStartMetrics.AppStartType.WARM, metrics.appStartType)
    assertFalse(metrics.isAppLaunchedInForeground)
    assertEquals(100, metrics.appStartTimeSpan.durationMs)
  }

  @Test
  fun `resolveHeadlessAppStartEndTime uses ApplicationStartInfo onCreate uptime timestamp`() {
    val appStartUptimeMs = 100L
    // START_TIMESTAMP_APPLICATION_ONCREATE is captured with SystemClock.uptimeNanos() (the same
    // base as TimeSpan) right before Application.onCreate is invoked, so it is used directly as
    // an uptime value marking the onCreate start, without any clock re-anchoring.
    val onCreateStartUptimeMs = 350L
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.startupTimestamps)
      .thenReturn(
        mapOf(
          ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE to
            TimeUnit.MILLISECONDS.toNanos(onCreateStartUptimeMs)
        )
      )
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    SentryShadowActivityManager.setImportance(RunningAppProcessInfo.IMPORTANCE_CACHED)
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.setStartedAt(appStartUptimeMs)
    metrics.setHeadlessAppStartListener {}

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)
    waitForMainLooperIdle()

    assertEquals(250, metrics.appStartTimeSpan.durationMs)
    assertFalse(metrics.applicationOnCreateTimeSpan.hasStarted())
  }

  @Test
  fun `listener fires when set after registerLifecycleCallbacks resolves type on API 35`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.startupTimestamps).thenReturn(emptyMap())
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    SentryShadowActivityManager.setImportance(RunningAppProcessInfo.IMPORTANCE_CACHED)

    val listenerCalls = AtomicInteger()
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.setStartedAt(100)

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)

    // Listener set AFTER registerLifecycleCallbacks — mirrors production ordering
    metrics.setHeadlessAppStartListener { listenerCalls.incrementAndGet() }
    waitForMainLooperIdle()

    assertEquals(AppStartMetrics.AppStartType.COLD, metrics.appStartType)
    assertFalse(metrics.isAppLaunchedInForeground)
    assertEquals(1, listenerCalls.get())
  }

  @Test
  fun `getAppStartReason maps ApplicationStartInfo reason to string on API 35`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.reason).thenReturn(ApplicationStartInfo.START_REASON_BROADCAST)
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    val metrics = AppStartMetrics.getInstance()

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)

    assertEquals("broadcast", metrics.appStartReason)
  }

  @Test
  fun `getAppStartReason returns null when no ApplicationStartInfo is available`() {
    SentryShadowActivityManager.setHistoricalProcessStartReasons(emptyList())
    val metrics = AppStartMetrics.getInstance()

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)

    assertNull(metrics.appStartReason)
  }

  @Test
  fun `getAppStartReason returns null for an unmapped reason`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.reason).thenReturn(Int.MAX_VALUE)
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    val metrics = AppStartMetrics.getInstance()

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)

    assertNull(metrics.appStartReason)
  }

  @Test
  fun `does not crash when getHistoricalProcessStartReasons throws RuntimeException`() {
    SentryShadowActivityManager.setHistoricalProcessStartReasonsException(
      RuntimeException("isolated process")
    )
    val metrics = AppStartMetrics.getInstance()

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)

    assertEquals(AppStartMetrics.AppStartType.UNKNOWN, metrics.appStartType)
    assertNull(metrics.appStartReason)
  }

  @Test
  fun `background start reason marks app as not launched in foreground`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.reason).thenReturn(ApplicationStartInfo.START_REASON_PUSH)
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    val metrics = AppStartMetrics.getInstance()

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)

    assertFalse(metrics.isAppLaunchedInForeground)
  }

  @Test
  fun `all background start reasons mark app as not launched in foreground`() {
    val backgroundReasons =
      listOf(
        ApplicationStartInfo.START_REASON_ALARM,
        ApplicationStartInfo.START_REASON_BACKUP,
        ApplicationStartInfo.START_REASON_BOOT_COMPLETE,
        ApplicationStartInfo.START_REASON_BROADCAST,
        ApplicationStartInfo.START_REASON_CONTENT_PROVIDER,
        ApplicationStartInfo.START_REASON_JOB,
        ApplicationStartInfo.START_REASON_PUSH,
        ApplicationStartInfo.START_REASON_SERVICE,
      )

    val app = ApplicationProvider.getApplicationContext<Application>()
    for (reason in backgroundReasons) {
      AppStartMetrics.getInstance().clear()
      SentryShadowActivityManager.reset()

      val mockStartInfo = mock<ApplicationStartInfo>()
      whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
      whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
      whenever(mockStartInfo.reason).thenReturn(reason)
      SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))

      AppStartMetrics.getInstance().registerLifecycleCallbacks(app)

      assertFalse(
        AppStartMetrics.getInstance().isAppLaunchedInForeground,
        "reason $reason should not be launched in foreground",
      )
    }
  }

  @Test
  fun `user-initiated start reason keeps app launched in foreground`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.reason).thenReturn(ApplicationStartInfo.START_REASON_LAUNCHER)
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    SentryShadowActivityManager.setImportance(RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
    val metrics = AppStartMetrics.getInstance()

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)

    assertTrue(metrics.isAppLaunchedInForeground)
  }

  @Test
  fun `background-spawned start is re-classified as warm on the first activity`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.reason).thenReturn(ApplicationStartInfo.START_REASON_PUSH)
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    val metrics = AppStartMetrics.getInstance()
    // App start span anchored at background process creation.
    metrics.appStartTimeSpan.setStartedAt(42)

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)

    // Background spawn detected: not launched in foreground, cold type from ApplicationStartInfo.
    assertFalse(metrics.isAppLaunchedInForeground)
    assertEquals(AppStartMetrics.AppStartType.COLD, metrics.appStartType)

    // User opens the app 20s later (well under the 1-minute warm threshold that previously was the
    // only way to catch this).
    val activityCreatedUptimeMs = 20_000L
    SystemClock.setCurrentTimeMillis(activityCreatedUptimeMs)
    metrics.onActivityCreated(mock<Activity>(), null)

    // The inflated cold start is re-classified as a warm start re-anchored at activity creation.
    assertEquals(AppStartMetrics.AppStartType.WARM, metrics.appStartType)
    assertTrue(metrics.isAppLaunchedInForeground)
    assertEquals(activityCreatedUptimeMs, metrics.appStartTimeSpan.startUptimeMs)
  }

  private fun waitForMainLooperIdle() {
    Handler(Looper.getMainLooper()).post {}
    Shadows.shadowOf(Looper.getMainLooper()).idle()
  }
}
