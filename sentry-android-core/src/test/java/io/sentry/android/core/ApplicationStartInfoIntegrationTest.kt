package io.sentry.android.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IScopes
import io.sentry.ISentryExecutorService
import io.sentry.protocol.SentryTransaction
import java.util.concurrent.Callable
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ApplicationStartInfoIntegrationTest {

  private lateinit var context: Context
  private lateinit var options: SentryAndroidOptions
  private lateinit var scopes: IScopes
  private lateinit var activityManager: ActivityManager
  private lateinit var executor: ISentryExecutorService
  private lateinit var buildInfoProvider: BuildInfoProvider

  @Before
  fun setup() {
    context = mock()
    options = SentryAndroidOptions()
    scopes = mock()
    activityManager = mock()
    executor = mock()
    buildInfoProvider = mock()

    // Setup default options
    options.isEnableApplicationStartInfo = true
    options.executorService = executor
    options.setLogger(mock<io.sentry.ILogger>())

    val mockDateProvider = mock<io.sentry.SentryDateProvider>()
    val mockDate = mock<io.sentry.SentryDate>()
    whenever(mockDate.nanoTimestamp()).thenReturn(System.currentTimeMillis() * 1_000_000L)
    whenever(mockDateProvider.now()).thenReturn(mockDate)
    options.dateProvider = mockDateProvider

    // Mock BuildInfoProvider to return API 35+
    whenever(buildInfoProvider.sdkInfoVersion).thenReturn(Build.VERSION_CODES.VANILLA_ICE_CREAM)

    // Execute tasks immediately for testing
    whenever(executor.submit(any<Callable<*>>())).thenAnswer {
      val callable = it.arguments[0] as Callable<*>
      callable.call()
      mock<java.util.concurrent.Future<*>>()
    }
    whenever(executor.submit(any<Runnable>())).thenAnswer {
      val runnable = it.arguments[0] as Runnable
      runnable.run()
      mock<java.util.concurrent.Future<*>>()
    }

    // Mock ActivityManager as system service
    whenever(context.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(activityManager)
  }

  @Test
  fun `integration does not register when disabled`() {
    options.isEnableApplicationStartInfo = false
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)

    integration.register(scopes, options)

    verify(executor, never()).submit(any<Runnable>())
  }

  @Test
  fun `integration registers completion listener on API 35+`() {
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager).addApplicationStartInfoCompletionListener(any(), any())
  }

  @Test
  fun `transaction includes correct tags from ApplicationStartInfo`() {
    val listenerCaptor = argumentCaptor<Consumer<android.app.ApplicationStartInfo>>()
    val transactionCaptor = argumentCaptor<SentryTransaction>()
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    val startInfo = createMockApplicationStartInfo()
    listenerCaptor.firstValue.accept(startInfo)

    verify(scopes).captureTransaction(transactionCaptor.capture(), anyOrNull(), anyOrNull())
    val transaction = transactionCaptor.firstValue
    assertNotNull(transaction.tags)
    assertTrue(transaction.tags!!.containsKey("start.reason"))
    assertTrue(transaction.tags!!.containsKey("start.type"))
    assertTrue(transaction.tags!!.containsKey("start.launch_mode"))
  }

  @Test
  fun `transaction includes start type from ApplicationStartInfo`() {
    val listenerCaptor = argumentCaptor<Consumer<android.app.ApplicationStartInfo>>()
    val transactionCaptor = argumentCaptor<SentryTransaction>()
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    val startInfo =
      createMockApplicationStartInfo(startType = android.app.ApplicationStartInfo.START_TYPE_COLD)
    listenerCaptor.firstValue.accept(startInfo)

    verify(scopes).captureTransaction(transactionCaptor.capture(), anyOrNull(), anyOrNull())
    assertEquals("cold", transactionCaptor.firstValue.tags!!["start.type"])
  }

  @Test
  fun `transaction includes launch mode from ApplicationStartInfo`() {
    val listenerCaptor = argumentCaptor<Consumer<android.app.ApplicationStartInfo>>()
    val transactionCaptor = argumentCaptor<SentryTransaction>()
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    val startInfo =
      createMockApplicationStartInfo(
        launchMode = android.app.ApplicationStartInfo.LAUNCH_MODE_STANDARD
      )
    listenerCaptor.firstValue.accept(startInfo)

    verify(scopes).captureTransaction(transactionCaptor.capture(), anyOrNull(), anyOrNull())
    assertEquals("standard", transactionCaptor.firstValue.tags!!["start.launch_mode"])
  }

  @Test
  fun `creates bind_application span when timestamp available`() {
    val listenerCaptor = argumentCaptor<Consumer<android.app.ApplicationStartInfo>>()
    val transactionCaptor = argumentCaptor<SentryTransaction>()
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    val startInfo =
      createMockApplicationStartInfo(forkTime = 1000000000L, bindApplicationTime = 1100000000L)
    listenerCaptor.firstValue.accept(startInfo)

    verify(scopes).captureTransaction(transactionCaptor.capture(), anyOrNull(), anyOrNull())
    val spans = transactionCaptor.firstValue.spans
    assertTrue(spans.any { it.op == "bind_application" })
  }

  @Test
  fun `creates ttid span when timestamp available`() {
    val listenerCaptor = argumentCaptor<Consumer<android.app.ApplicationStartInfo>>()
    val transactionCaptor = argumentCaptor<SentryTransaction>()
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    val startInfo =
      createMockApplicationStartInfo(forkTime = 1000000000L, firstFrameTime = 1500000000L)
    listenerCaptor.firstValue.accept(startInfo)

    verify(scopes).captureTransaction(transactionCaptor.capture(), anyOrNull(), anyOrNull())
    val spans = transactionCaptor.firstValue.spans
    assertTrue(spans.any { it.op == "ttid" })
  }

  @Test
  fun `creates ttfd span when timestamp available`() {
    val listenerCaptor = argumentCaptor<Consumer<android.app.ApplicationStartInfo>>()
    val transactionCaptor = argumentCaptor<SentryTransaction>()
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    val startInfo =
      createMockApplicationStartInfo(forkTime = 1000000000L, fullyDrawnTime = 2000000000L)
    listenerCaptor.firstValue.accept(startInfo)

    verify(scopes).captureTransaction(transactionCaptor.capture(), anyOrNull(), anyOrNull())
    val spans = transactionCaptor.firstValue.spans
    assertTrue(spans.any { it.op == "ttfd" })
  }

  @Test
  fun `closes integration without errors`() {
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    integration.close()
    // Should not throw exception
  }

  @Test
  fun `transaction name is app_start`() {
    val listenerCaptor = argumentCaptor<Consumer<android.app.ApplicationStartInfo>>()
    val transactionCaptor = argumentCaptor<SentryTransaction>()
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    val startInfo = createMockApplicationStartInfo()
    listenerCaptor.firstValue.accept(startInfo)

    verify(scopes).captureTransaction(transactionCaptor.capture(), anyOrNull(), anyOrNull())
    assertEquals("app.start", transactionCaptor.firstValue.transaction)
  }

  @Test
  fun `does not register on API lower than 35`() {
    whenever(buildInfoProvider.sdkInfoVersion).thenReturn(34)
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)

    integration.register(scopes, options)

    verify(activityManager, never()).addApplicationStartInfoCompletionListener(any(), any())
  }

  // Helper methods
  private fun createMockApplicationStartInfo(
    forkTime: Long = 1000000000L, // nanoseconds
    bindApplicationTime: Long = 0L,
    firstFrameTime: Long = 0L,
    fullyDrawnTime: Long = 0L,
    reason: Int = android.app.ApplicationStartInfo.START_REASON_LAUNCHER,
    startType: Int = android.app.ApplicationStartInfo.START_TYPE_COLD,
    launchMode: Int = android.app.ApplicationStartInfo.LAUNCH_MODE_STANDARD,
  ): android.app.ApplicationStartInfo {
    val startInfo = mock<android.app.ApplicationStartInfo>()

    val timestamps = mutableMapOf<Int, Long>()
    timestamps[android.app.ApplicationStartInfo.START_TIMESTAMP_FORK] = forkTime
    if (bindApplicationTime > 0) {
      timestamps[android.app.ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION] =
        bindApplicationTime
    }
    if (firstFrameTime > 0) {
      timestamps[android.app.ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME] = firstFrameTime
    }
    if (fullyDrawnTime > 0) {
      timestamps[android.app.ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN] = fullyDrawnTime
    }

    whenever(startInfo.reason).thenReturn(reason)
    whenever(startInfo.startType).thenReturn(startType)
    whenever(startInfo.launchMode).thenReturn(launchMode)
    whenever(startInfo.startupTimestamps).thenReturn(timestamps)

    return startInfo
  }
}
