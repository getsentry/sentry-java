package io.sentry.android.core.performance

import android.app.Application
import android.app.ApplicationStartInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
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
  fun `normalizes app start reason using ApplicationStartInfo on API 35`() {
    val reasons =
      mapOf(
        ApplicationStartInfo.START_REASON_ALARM to "alarm",
        ApplicationStartInfo.START_REASON_BACKUP to "backup",
        ApplicationStartInfo.START_REASON_BOOT_COMPLETE to "boot_complete",
        ApplicationStartInfo.START_REASON_BROADCAST to "broadcast",
        ApplicationStartInfo.START_REASON_CONTENT_PROVIDER to "content_provider",
        ApplicationStartInfo.START_REASON_JOB to "job",
        ApplicationStartInfo.START_REASON_LAUNCHER to "launcher",
        ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS to "launcher_recents",
        ApplicationStartInfo.START_REASON_OTHER to "other",
        ApplicationStartInfo.START_REASON_PUSH to "push",
        ApplicationStartInfo.START_REASON_SERVICE to "service",
        ApplicationStartInfo.START_REASON_START_ACTIVITY to "start_activity",
        999 to "unknown",
      )

    reasons.forEach { (reason, expected) ->
      AppStartMetrics.getInstance().clear()
      SentryShadowActivityManager.reset()
      val mockStartInfo = mock<ApplicationStartInfo>()
      whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
      whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
      whenever(mockStartInfo.reason).thenReturn(reason)
      SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))

      val app = ApplicationProvider.getApplicationContext<Application>()
      AppStartMetrics.getInstance().registerLifecycleCallbacks(app)

      assertEquals(expected, AppStartMetrics.getInstance().appStartReason)
    }
  }

  @Test
  fun `app start reason is null when ApplicationStartInfo list is empty`() {
    SentryShadowActivityManager.setHistoricalProcessStartReasons(emptyList())
    val metrics = AppStartMetrics.getInstance()

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)

    assertNull(metrics.appStartReason)
  }

  @Test
  fun `checkCreateTimeOnMain keeps appStartType COLD when ApplicationStartInfo reports cold start`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.startupTimestamps).thenReturn(emptyMap())
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    val listenerCalls = AtomicInteger()
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.setStartedAt(100)
    metrics.setOnNoActivityStartedListener { listenerCalls.incrementAndGet() }

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)
    waitForMainLooperIdle()

    assertEquals(AppStartMetrics.AppStartType.COLD, metrics.appStartType)
    assertFalse(metrics.isAppLaunchedInForeground)
    assertEquals(1, listenerCalls.get())
  }

  @Test
  fun `resolveNonActivityAppStartEndTime converts ApplicationStartInfo timestamp to uptime`() {
    val processStartUptimeMs = 100L
    val processStartElapsedMs = 10_000L
    val onCreateElapsedMs = 10_250L
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.startupTimestamps)
      .thenReturn(
        mapOf(
          ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE to
            TimeUnit.MILLISECONDS.toNanos(onCreateElapsedMs)
        )
      )
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    SentryShadowProcess.setStartUptimeMillis(processStartUptimeMs)
    SentryShadowProcess.setStartElapsedRealtime(processStartElapsedMs)
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.setStartedAt(processStartUptimeMs)

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)
    waitForMainLooperIdle()

    assertEquals(250, metrics.appStartTimeSpan.durationMs)
    assertFalse(metrics.applicationOnCreateTimeSpan.hasStarted())
  }

  @Test
  fun `resolveNonActivityAppStartEndTime falls back when ApplicationStartInfo duration is invalid`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.startupTimestamps)
      .thenReturn(
        mapOf(
          ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE to TimeUnit.MINUTES.toNanos(2)
        )
      )
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    SentryShadowProcess.setStartUptimeMillis(100)
    SentryShadowProcess.setStartElapsedRealtime(0)
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.setStartedAt(100)
    metrics.setClassLoadedUptimeMs(200)

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)
    waitForMainLooperIdle()

    assertEquals(100, metrics.appStartTimeSpan.durationMs)
  }

  private fun waitForMainLooperIdle() {
    Handler(Looper.getMainLooper()).post {}
    Shadows.shadowOf(Looper.getMainLooper()).idle()
  }
}
