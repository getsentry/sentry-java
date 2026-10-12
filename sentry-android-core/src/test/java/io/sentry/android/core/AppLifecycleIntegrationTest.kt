package io.sentry.android.core

import android.app.ActivityManager
import android.app.AnrTypes
import android.app.AnrWarningResult
import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.ISentryExecutorService
import io.sentry.SentryLevel
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
import io.sentry.test.NonOverridableNoOpSentryExecutorService
import java.util.concurrent.CountDownLatch
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class AppLifecycleIntegrationTest {
  private class Fixture {
    val scopes = mock<IScopes>()
    val options = SentryAndroidOptions()

    fun getSut(): AppLifecycleIntegration {
      return AppLifecycleIntegration(ApplicationProvider.getApplicationContext(), mock())
    }
  }

  private val fixture = Fixture()

  /**
   * Robolectric has no shadow for registerAnrWarningListener and offers no way to deliver a
   * warning, so the ActivityManager itself is mocked. Everything around it is real: the mock is
   * injected into the Robolectric application context as the system service.
   */
  private fun getSutWithActivityManager(
    activityManager: ActivityManager,
    sdkVersion: Int = 37,
    executorService: ISentryExecutorService = ImmediateExecutorService(),
  ): AppLifecycleIntegration {
    val application = ApplicationProvider.getApplicationContext<Application>()
    shadowOf(application).setSystemService(Context.ACTIVITY_SERVICE, activityManager)
    val buildInfoProvider = mock<BuildInfoProvider>()
    whenever(buildInfoProvider.sdkInfoVersion).thenReturn(sdkVersion)
    fixture.options.executorService = executorService
    return AppLifecycleIntegration(application, buildInfoProvider)
  }

  private fun anrWarningResult(
    anrType: Int = AnrTypes.ANR_TYPE_BROADCAST_OF_INTENT,
    anrId: Int = 7,
    description: String? = "Broadcast of Intent { act=android.intent.action.SCREEN_ON }",
    timeoutMillis: Long = 10_000,
    consumedMillis: Long = 7_500,
  ): AnrWarningResult =
    mock<AnrWarningResult>().also {
      whenever(it.anrType).thenReturn(anrType)
      whenever(it.anrId).thenReturn(anrId)
      whenever(it.description).thenReturn(description)
      whenever(it.timeoutMillis).thenReturn(timeoutMillis)
      whenever(it.consumedMillis).thenReturn(consumedMillis)
    }

  @Test
  @Config(sdk = [37])
  fun `ANR warning listener adds a breadcrumb and is unregistered on close`() {
    val activityManager = mock<ActivityManager>()
    val sut = getSutWithActivityManager(activityManager)

    sut.register(fixture.scopes, fixture.options)

    val consumerCaptor = argumentCaptor<Consumer<AnrWarningResult>>()
    verify(activityManager).registerAnrWarningListener(any(), consumerCaptor.capture())

    consumerCaptor.firstValue.accept(anrWarningResult())

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("system", it.type)
          assertEquals("app.anr", it.category)
          assertEquals(SentryLevel.WARNING, it.level)
          assertEquals("ANR warning", it.message)
          assertEquals("broadcast_of_intent", it.getData("anr_type"))
          assertEquals(7, it.getData("anr_id"))
          assertEquals(10_000L, it.getData("timeout_ms"))
          assertEquals(7_500L, it.getData("consumed_ms"))
          assertEquals(
            "Broadcast of Intent { act=android.intent.action.SCREEN_ON }",
            it.getData("description"),
          )
        }
      )

    sut.close()

    verify(activityManager).unregisterAnrWarningListener(consumerCaptor.firstValue)
  }

  @Test
  @Config(sdk = [37])
  fun `unknown ANR types are reported verbatim instead of being dropped`() {
    val activityManager = mock<ActivityManager>()
    val sut = getSutWithActivityManager(activityManager)

    sut.register(fixture.scopes, fixture.options)

    val consumerCaptor = argumentCaptor<Consumer<AnrWarningResult>>()
    verify(activityManager).registerAnrWarningListener(any(), consumerCaptor.capture())
    consumerCaptor.firstValue.accept(anrWarningResult(anrType = 42))

    verify(fixture.scopes)
      .addBreadcrumb(check<Breadcrumb> { assertEquals("unknown (42)", it.getData("anr_type")) })
  }

  @Test
  @Config(sdk = [37])
  fun `ANR warning listener is not registered when app lifecycle breadcrumbs are disabled`() {
    val activityManager = mock<ActivityManager>()
    val sut = getSutWithActivityManager(activityManager)
    fixture.options.isEnableAppLifecycleBreadcrumbs = false

    sut.register(fixture.scopes, fixture.options)

    verify(activityManager, never()).registerAnrWarningListener(any(), any())
  }

  @Test
  @Config(sdk = [37])
  fun `the deprecated no-arg constructor installs the integration without the ANR warning listener`() {
    @Suppress("DEPRECATION") val sut = AppLifecycleIntegration()

    sut.register(fixture.scopes, fixture.options)

    assertNotNull(sut.watcher)

    sut.close()

    assertNull(sut.watcher)
  }

  @Test
  @Config(sdk = [37])
  fun `ANR warning listener is not registered below API 37`() {
    val activityManager = mock<ActivityManager>()
    val sut = getSutWithActivityManager(activityManager, sdkVersion = 36)

    sut.register(fixture.scopes, fixture.options)

    verify(activityManager, never()).registerAnrWarningListener(any(), any())
  }

  @Test
  @Config(sdk = [37])
  fun `a failure to register the ANR warning listener still installs the integration`() {
    val activityManager = mock<ActivityManager>()
    whenever(activityManager.registerAnrWarningListener(any(), any()))
      .thenThrow(RuntimeException("binder died"))
    val sut = getSutWithActivityManager(activityManager)

    sut.register(fixture.scopes, fixture.options)

    assertNotNull(sut.watcher)

    sut.close()

    verify(activityManager, never()).unregisterAnrWarningListener(any())
  }

  @Test
  @Config(sdk = [37])
  fun `closing twice unregisters the ANR warning listener only once`() {
    val activityManager = mock<ActivityManager>()
    val sut = getSutWithActivityManager(activityManager)

    sut.register(fixture.scopes, fixture.options)
    sut.close()
    sut.close()

    verify(activityManager, times(1)).unregisterAnrWarningListener(any())
  }

  @Test
  fun `When AppLifecycleIntegration is added, lifecycle watcher should be started`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertNotNull(sut.watcher)
  }

  @Test
  fun `When SessionTracking and AppLifecycle breadcrumbs are disabled, lifecycle watcher should not be started`() {
    val sut = fixture.getSut()
    fixture.options.apply {
      isEnableAppLifecycleBreadcrumbs = false
      isEnableAutoSessionTracking = false
    }

    sut.register(fixture.scopes, fixture.options)

    assertNull(sut.watcher)
  }

  @Test
  fun `When AppLifecycleIntegration is closed, lifecycle watcher should be closed`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertNotNull(sut.watcher)

    sut.close()

    assertNull(sut.watcher)
  }

  @Test
  fun `When AppLifecycleIntegration is closed from a background thread, watcher is set to null`() {
    val sut = fixture.getSut()
    val latch = CountDownLatch(1)

    sut.register(fixture.scopes, fixture.options)

    assertNotNull(sut.watcher)

    Thread {
        sut.close()
        latch.countDown()
      }
      .start()

    latch.await()

    // ensure all messages on main looper got processed
    shadowOf(Looper.getMainLooper()).idle()

    assertNull(sut.watcher)
  }

  @Test
  fun `When AppLifecycleIntegration is closed, AppState unregisterLifecycleObserver is called`() {
    val sut = fixture.getSut()
    val appState = AppState.getInstance()

    sut.register(fixture.scopes, fixture.options)

    // Verify that lifecycleObserver is not null after registration
    assertNotNull(appState.lifecycleObserver)

    sut.close()

    // Verify that lifecycleObserver is null after unregistering
    assertNull(appState.lifecycleObserver)
  }

  @Test
  fun `ANR type names are mapped`() {
    assertEquals("other", AppLifecycleIntegration.getAnrTypeName(AnrTypes.ANR_TYPE_OTHER))
    assertEquals(
      "input_dispatch_no_focused_window",
      AppLifecycleIntegration.getAnrTypeName(AnrTypes.ANR_TYPE_INPUT_DISPATCH_NO_FOCUSED_WINDOW),
    )
    assertEquals(
      "broadcast_of_intent",
      AppLifecycleIntegration.getAnrTypeName(AnrTypes.ANR_TYPE_BROADCAST_OF_INTENT),
    )
    assertEquals(
      "start_foreground_service",
      AppLifecycleIntegration.getAnrTypeName(AnrTypes.ANR_TYPE_START_FOREGROUND_SERVICE),
    )
    assertEquals(
      "execute_service",
      AppLifecycleIntegration.getAnrTypeName(AnrTypes.ANR_TYPE_EXECUTE_SERVICE),
    )
    assertEquals(
      "content_provider_not_responding",
      AppLifecycleIntegration.getAnrTypeName(AnrTypes.ANR_TYPE_CONTENT_PROVIDER_NOT_RESPONDING),
    )
    assertEquals(
      "app_triggered",
      AppLifecycleIntegration.getAnrTypeName(AnrTypes.ANR_TYPE_APP_TRIGGERED),
    )
    assertEquals(
      "foreground_short_service_timeout",
      AppLifecycleIntegration.getAnrTypeName(AnrTypes.ANR_TYPE_FOREGROUND_SHORT_SERVICE_TIMEOUT),
    )
    assertEquals(
      "job_service_start",
      AppLifecycleIntegration.getAnrTypeName(AnrTypes.ANR_TYPE_JOB_SERVICE_START),
    )
    assertEquals(
      "application_start",
      AppLifecycleIntegration.getAnrTypeName(AnrTypes.ANR_TYPE_APPLICATION_START),
    )
    assertEquals("unknown (42)", AppLifecycleIntegration.getAnrTypeName(42))
  }

  @Test
  @Config(sdk = [37])
  fun `the binder calls run on the executor service, not on the calling thread`() {
    val activityManager = mock<ActivityManager>()
    val executorService = DeferredExecutorService()
    val sut = getSutWithActivityManager(activityManager, executorService = executorService)

    sut.register(fixture.scopes, fixture.options)
    verify(activityManager, never()).registerAnrWarningListener(any(), any())

    executorService.runAll()
    verify(activityManager).registerAnrWarningListener(any(), any())

    sut.close()
    verify(activityManager, never()).unregisterAnrWarningListener(any())

    executorService.runAll()
    verify(activityManager).unregisterAnrWarningListener(any())
  }

  @Test
  @Config(sdk = [37])
  fun `a register task still queued when close runs does not register the listener`() {
    val activityManager = mock<ActivityManager>()
    val executorService = DeferredExecutorService()
    val sut = getSutWithActivityManager(activityManager, executorService = executorService)

    sut.register(fixture.scopes, fixture.options)
    sut.close()
    executorService.runAll()

    verify(activityManager, never()).registerAnrWarningListener(any(), any())
    verify(activityManager, never()).unregisterAnrWarningListener(any())
  }

  @Test
  @Config(sdk = [37])
  fun `a dropped register task leaves the listener uninstalled`() {
    val activityManager = mock<ActivityManager>()
    val sut =
      getSutWithActivityManager(
        activityManager,
        executorService = NonOverridableNoOpSentryExecutorService(),
      )

    sut.register(fixture.scopes, fixture.options)

    verify(activityManager, never()).registerAnrWarningListener(any(), any())
    assertNotNull(sut.watcher)
  }

  @Test
  @Config(sdk = [37])
  fun `close unregisters inline when the executor service drops the task`() {
    val activityManager = mock<ActivityManager>()
    val sut = getSutWithActivityManager(activityManager)

    sut.register(fixture.scopes, fixture.options)
    verify(activityManager).registerAnrWarningListener(any(), any())

    // The executor is typically already shut down by the time integrations are closed.
    fixture.options.executorService = NonOverridableNoOpSentryExecutorService()
    sut.close()

    verify(activityManager).unregisterAnrWarningListener(any())
  }
}
