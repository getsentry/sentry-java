package io.sentry.android.core

import android.app.Activity
import android.app.Application
import android.app.ApplicationStartInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.NoOpLogger
import io.sentry.Scopes
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.android.core.performance.AppStartMetrics
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(
  sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM],
  shadows = [SentryShadowProcess::class, SentryShadowActivityManager::class],
)
class ActivityLifecycleIntegrationApi35Test {
  @BeforeTest
  fun setup() {
    AppStartMetrics.getInstance().clear()
    SentryShadowProcess.setStartUptimeMillis(42)
    SentryShadowActivityManager.reset()
    AppStartMetrics.getInstance().setClassLoadedUptimeMs(42)
  }

  @Test
  fun `non-activity standalone app start transaction includes app start reason`() {
    setAppStartInfo(ApplicationStartInfo.START_REASON_PUSH)

    val app = ApplicationProvider.getApplicationContext<Application>()
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.setStartedAt(100)
    metrics.registerLifecycleCallbacks(app)

    val options =
      SentryAndroidOptions().apply {
        dsn = "https://key@sentry.io/proj"
        tracesSampleRate = 1.0
        isEnableStandaloneAppStartTracing = true
      }
    val scopes = mock<Scopes>()
    whenever(scopes.options).thenReturn(options)

    val createdTransactions = mutableListOf<SentryTracer>()
    val contextCaptor = argumentCaptor<TransactionContext>()
    val optionCaptor = argumentCaptor<TransactionOptions>()
    whenever(scopes.startTransaction(contextCaptor.capture(), optionCaptor.capture())).thenAnswer {
      SentryTracer(contextCaptor.lastValue, scopes, optionCaptor.lastValue).also {
        createdTransactions.add(it)
      }
    }

    val sut = ActivityLifecycleIntegration(app, BuildInfoProvider(NoOpLogger.getInstance()), mock())
    try {
      sut.register(scopes, options)
      waitForMainLooperIdle()

      val appStartTransaction =
        createdTransactions.single {
          it.spanContext.operation == ActivityLifecycleIntegration.STANDALONE_APP_START_OP
        }
      assertEquals("push", appStartTransaction.getData("app.vitals.start.reason"))
      assertNull(appStartTransaction.getData("app.vitals.start.screen"))
    } finally {
      sut.close()
    }
  }

  @Test
  fun `activity standalone app start transaction includes app start reason`() {
    setAppStartInfo(ApplicationStartInfo.START_REASON_LAUNCHER)

    val app = ApplicationProvider.getApplicationContext<Application>()
    val metrics = AppStartMetrics.getInstance()
    metrics.appStartTimeSpan.setStartedAt(100)
    metrics.registerLifecycleCallbacks(app)

    val options =
      SentryAndroidOptions().apply {
        dsn = "https://key@sentry.io/proj"
        tracesSampleRate = 1.0
        isEnableStandaloneAppStartTracing = true
      }
    val scopes = mock<Scopes>()
    whenever(scopes.options).thenReturn(options)

    val createdTransactions = mutableListOf<SentryTracer>()
    val contextCaptor = argumentCaptor<TransactionContext>()
    val optionCaptor = argumentCaptor<TransactionOptions>()
    whenever(scopes.startTransaction(contextCaptor.capture(), optionCaptor.capture())).thenAnswer {
      SentryTracer(contextCaptor.lastValue, scopes, optionCaptor.lastValue).also {
        createdTransactions.add(it)
      }
    }

    val sut = ActivityLifecycleIntegration(app, BuildInfoProvider(NoOpLogger.getInstance()), mock())
    try {
      sut.register(scopes, options)
      sut.onActivityCreated(mock<Activity>(), null)

      val appStartTransaction =
        createdTransactions.single {
          it.spanContext.operation == ActivityLifecycleIntegration.STANDALONE_APP_START_OP
        }
      assertEquals("launcher", appStartTransaction.getData("app.vitals.start.reason"))
      assertEquals("Activity", appStartTransaction.getData("app.vitals.start.screen"))
    } finally {
      sut.close()
    }
  }

  private fun setAppStartInfo(reason: Int) {
    val appStartEndMs = 250L
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.reason).thenReturn(reason)
    whenever(mockStartInfo.startupTimestamps)
      .thenReturn(
        mapOf(
          ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE to
            TimeUnit.MILLISECONDS.toNanos(appStartEndMs)
        )
      )
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))
  }

  private fun waitForMainLooperIdle() {
    Handler(Looper.getMainLooper()).post {}
    Shadows.shadowOf(Looper.getMainLooper()).idle()
  }
}
