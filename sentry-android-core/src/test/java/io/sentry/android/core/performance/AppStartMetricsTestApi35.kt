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
  fun `no activity start keeps COLD appStartType from ApplicationStartInfo`() {
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
  fun `known ApplicationStartInfo type without listener preserves legacy no activity behavior`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_WARM)
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.setStartedAt(100)

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)
    waitForMainLooperIdle()

    assertEquals(AppStartMetrics.AppStartType.WARM, metrics.appStartType)
    assertFalse(metrics.appStartTimeSpan.hasStopped())
  }

  @Test
  fun `resolveNonActivityAppStartEndTime converts ApplicationStartInfo timestamp to uptime`() {
    val processStartUptimeMs = 100L
    val processStartElapsedMs = 10_000L
    val onCreateElapsedRealtimeMs = 10_250L
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.startupTimestamps)
      .thenReturn(
        mapOf(
          ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE to
            TimeUnit.MILLISECONDS.toNanos(onCreateElapsedRealtimeMs)
        )
      )
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
    SentryShadowProcess.setStartUptimeMillis(processStartUptimeMs)
    SentryShadowProcess.setStartElapsedRealtime(processStartElapsedMs)
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.setStartedAt(processStartUptimeMs)
    metrics.setOnNoActivityStartedListener {}

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
    metrics.setOnNoActivityStartedListener {}

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)
    waitForMainLooperIdle()

    assertEquals(100, metrics.appStartTimeSpan.durationMs)
  }

  @Test
  fun `listener fires when set after registerLifecycleCallbacks resolves type on API 35`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.startupTimestamps).thenReturn(emptyMap())
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))

    val listenerCalls = AtomicInteger()
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.setStartedAt(100)

    val app = ApplicationProvider.getApplicationContext<Application>()
    metrics.registerLifecycleCallbacks(app)

    // Listener set AFTER registerLifecycleCallbacks — mirrors production ordering
    metrics.setOnNoActivityStartedListener { listenerCalls.incrementAndGet() }
    waitForMainLooperIdle()

    assertEquals(AppStartMetrics.AppStartType.COLD, metrics.appStartType)
    assertFalse(metrics.isAppLaunchedInForeground)
    assertEquals(1, listenerCalls.get())
  }

  private fun waitForMainLooperIdle() {
    Handler(Looper.getMainLooper()).post {}
    Shadows.shadowOf(Looper.getMainLooper()).idle()
  }
}
