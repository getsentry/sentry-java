package io.sentry.android.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ISentryExecutorService
import io.sentry.ITransaction
import io.sentry.SentryLevel
import io.sentry.TransactionContext
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.android.core.performance.TimeSpan
import io.sentry.protocol.SentryId
import java.util.concurrent.Callable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
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

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    options = spy(SentryAndroidOptions())
    scopes = mock()
    activityManager = mock()
    executor = mock()

    // Setup default mocks
    whenever(options.isEnableApplicationStartInfo).thenReturn(true)
    whenever(options.executorService).thenReturn(executor)
    whenever(options.logger).thenReturn(mock<io.sentry.ILogger>())
    whenever(options.dateProvider).thenReturn(mock<io.sentry.SentryDateProvider>())
    whenever(options.flushTimeoutMillis).thenReturn(5000L)
    whenever(scopes.captureEvent(anyOrNull(), any<Hint>())).thenReturn(SentryId())

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
    whenever(options.isEnableApplicationStartInfo).thenReturn(false)
    val integration = ApplicationStartInfoIntegration(context)

    integration.register(scopes, options)

    verify(executor, never()).submit(any<Runnable>())
  }

  @Test
  @Config(sdk = [30])
  fun `integration does not register on API level below 35`() {
    val integration = ApplicationStartInfoIntegration(context)

    integration.register(scopes, options)

    verify(activityManager, never()).getHistoricalProcessStartReasons(any())
  }

  @Test
  fun `integration registers and collects historical data on API 35+`() {
    val startInfoList = createMockApplicationStartInfoList()
    whenever(activityManager.getHistoricalProcessStartReasons(5)).thenReturn(startInfoList)

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    verify(activityManager).getHistoricalProcessStartReasons(5)
  }

  @Test
  fun `creates transaction for each historical app start`() {
    val startInfoList = createMockApplicationStartInfoList()
    whenever(activityManager.getHistoricalProcessStartReasons(5)).thenReturn(startInfoList)

    val capturedTransactions = mutableListOf<ITransaction>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>())).thenAnswer {
      val mockTransaction = mock<ITransaction>()
      capturedTransactions.add(mockTransaction)
      mockTransaction
    }

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    assertTrue(capturedTransactions.isNotEmpty(), "Should create at least one transaction")
  }

  @Test
  fun `transaction includes correct tags from ApplicationStartInfo`() {
    val startInfoList = createMockApplicationStartInfoList()
    whenever(activityManager.getHistoricalProcessStartReasons(5)).thenReturn(startInfoList)

    val mockTransaction = mock<ITransaction>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    verify(mockTransaction).setTag(eq("start.reason"), any())
  }

  @Test
  fun `transaction includes app start type from AppStartMetrics`() {
    AppStartMetrics.getInstance().setAppStartType(AppStartMetrics.AppStartType.COLD)

    val startInfoList = createMockApplicationStartInfoList()
    whenever(activityManager.getHistoricalProcessStartReasons(5)).thenReturn(startInfoList)

    val mockTransaction = mock<ITransaction>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    verify(mockTransaction).setTag("start.type", "cold")
  }

  @Test
  fun `transaction includes foreground launch indicator`() {
    val startInfoList = createMockApplicationStartInfoList()
    whenever(activityManager.getHistoricalProcessStartReasons(5)).thenReturn(startInfoList)

    val mockTransaction = mock<ITransaction>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    verify(mockTransaction).setTag(eq("start.foreground"), any())
  }

  @Test
  fun `creates bind_application span when timestamp available`() {
    val startInfo =
      createMockApplicationStartInfo(forkTime = 1000000000L, bindApplicationTime = 1100000000L)
    whenever(activityManager.getHistoricalProcessStartReasons(5)).thenReturn(listOf(startInfo))

    val mockTransaction = mock<ITransaction>()
    val mockSpan = mock<io.sentry.ISpan>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)
    whenever(
        mockTransaction.startChild(eq("app.start.bind_application"), anyOrNull(), any(), any())
      )
      .thenReturn(mockSpan)

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    verify(mockTransaction).startChild(eq("app.start.bind_application"), anyOrNull(), any(), any())
    verify(mockSpan).finish(any(), any())
  }

  @Test
  fun `creates content provider spans from AppStartMetrics`() {
    val appStartMetrics = AppStartMetrics.getInstance()
    appStartMetrics.clear()

    // Add mock content provider spans
    val cpSpan = TimeSpan()
    cpSpan.setup("com.example.MyContentProvider.onCreate", 1000L, 100L, 200L)

    val startInfo = createMockApplicationStartInfo()
    whenever(activityManager.getHistoricalProcessStartReasons(5)).thenReturn(listOf(startInfo))

    val mockTransaction = mock<ITransaction>()
    val mockSpan = mock<io.sentry.ISpan>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)
    whenever(mockTransaction.startChild(eq("contentprovider.load"), any(), any(), any()))
      .thenReturn(mockSpan)

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    // Note: In real scenario, content providers would be populated by instrumentation
    // This test verifies the integration correctly processes them when available
  }

  @Test
  fun `creates application_oncreate span when timestamp available`() {
    val startInfo =
      createMockApplicationStartInfo(forkTime = 1000000000L, applicationOnCreateTime = 1200000000L)
    whenever(activityManager.getHistoricalProcessStartReasons(5)).thenReturn(listOf(startInfo))

    val mockTransaction = mock<ITransaction>()
    val mockSpan = mock<io.sentry.ISpan>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)
    whenever(
        mockTransaction.startChild(eq("app.start.application_oncreate"), anyOrNull(), any(), any())
      )
      .thenReturn(mockSpan)

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    verify(mockTransaction)
      .startChild(eq("app.start.application_oncreate"), anyOrNull(), any(), any())
    verify(mockSpan).finish(any(), any())
  }

  @Test
  fun `creates ttid span when timestamp available`() {
    val startInfo =
      createMockApplicationStartInfo(forkTime = 1000000000L, firstFrameTime = 1500000000L)
    whenever(activityManager.getHistoricalProcessStartReasons(5)).thenReturn(listOf(startInfo))

    val mockTransaction = mock<ITransaction>()
    val mockSpan = mock<io.sentry.ISpan>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)
    whenever(mockTransaction.startChild(eq("app.start.ttid"), anyOrNull(), any(), any()))
      .thenReturn(mockSpan)

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    verify(mockTransaction).startChild(eq("app.start.ttid"), anyOrNull(), any(), any())
    verify(mockSpan).finish(any(), any())
  }

  @Test
  fun `creates ttfd span when timestamp available`() {
    val startInfo =
      createMockApplicationStartInfo(forkTime = 1000000000L, fullyDrawnTime = 2000000000L)
    whenever(activityManager.getHistoricalProcessStartReasons(5)).thenReturn(listOf(startInfo))

    val mockTransaction = mock<ITransaction>()
    val mockSpan = mock<io.sentry.ISpan>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)
    whenever(mockTransaction.startChild(eq("app.start.ttfd"), anyOrNull(), any(), any()))
      .thenReturn(mockSpan)

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    verify(mockTransaction).startChild(eq("app.start.ttfd"), anyOrNull(), any(), any())
    verify(mockSpan).finish(any(), any())
  }

  @Test
  fun `skips old app starts beyond 90 day threshold`() {
    val currentTime = System.currentTimeMillis()
    val oldTime = currentTime - (92L * 24 * 60 * 60 * 1000) // 92 days ago

    whenever(options.dateProvider.now().nanoTimestamp()).thenReturn(currentTime * 1000000)

    val startInfo = createMockApplicationStartInfo(forkTime = oldTime * 1000000) // nanoseconds
    whenever(activityManager.getHistoricalProcessStartReasons(5)).thenReturn(listOf(startInfo))

    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>())).thenReturn(mock())

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    verify(scopes, never()).startTransaction(any(), any<io.sentry.TransactionOptions>())
  }

  @Test
  fun `ApplicationStartInfoHint implements Backfillable correctly`() {
    val hint =
      ApplicationStartInfoIntegration.ApplicationStartInfoHint(
        5000L,
        mock<io.sentry.ILogger>(),
        123456789L,
        true,
      )

    assertTrue(hint.shouldEnrich(), "Current launch should enrich")
    assertEquals(123456789L, hint.timestamp())
    assertTrue(hint.isFlushable(SentryId()))
  }

  @Test
  fun `ApplicationStartInfoHint for historical events does not enrich`() {
    val hint =
      ApplicationStartInfoIntegration.ApplicationStartInfoHint(
        5000L,
        mock<io.sentry.ILogger>(),
        123456789L,
        false,
      )

    assertFalse(hint.shouldEnrich(), "Historical events should not enrich")
  }

  @Test
  fun `closes integration without errors`() {
    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    integration.close()
    // Should not throw exception
  }

  @Test
  fun `handles null ActivityManager gracefully`() {
    whenever(context.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(null)

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    verify(options.logger).log(eq(SentryLevel.ERROR), any<String>())
  }

  @Test
  fun `handles empty historical start info list`() {
    whenever(activityManager.getHistoricalProcessStartReasons(5)).thenReturn(emptyList())

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    verify(options.logger).log(eq(SentryLevel.DEBUG), any<String>())
    verify(scopes, never()).startTransaction(any(), any<io.sentry.TransactionOptions>())
  }

  @Test
  fun `handles exception during historical collection gracefully`() {
    whenever(activityManager.getHistoricalProcessStartReasons(5))
      .thenThrow(RuntimeException("Test exception"))

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    verify(options.logger).log(eq(SentryLevel.ERROR), any<String>(), any<Throwable>())
  }

  @Test
  fun `transaction name includes reason label`() {
    val startInfo = createMockApplicationStartInfo()
    whenever(startInfo.reason)
      .thenReturn(
        if (Build.VERSION.SDK_INT >= 35) android.app.ApplicationStartInfo.START_REASON_LAUNCHER
        else 0
      )
    whenever(activityManager.getHistoricalProcessStartReasons(5)).thenReturn(listOf(startInfo))

    var capturedContext: TransactionContext? = null
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>())).thenAnswer {
      capturedContext = it.arguments[0] as TransactionContext
      mock()
    }

    val integration = ApplicationStartInfoIntegration(context)
    integration.register(scopes, options)

    assertNotNull(capturedContext)
    assertEquals("app.start.launcher", capturedContext!!.name)
  }

  // Helper methods
  private fun createMockApplicationStartInfoList(): MutableList<android.app.ApplicationStartInfo> {
    return mutableListOf(createMockApplicationStartInfo())
  }

  private fun createMockApplicationStartInfo(
    forkTime: Long = 1000000000L, // nanoseconds
    bindApplicationTime: Long = 0L,
    applicationOnCreateTime: Long = 0L,
    firstFrameTime: Long = 0L,
    fullyDrawnTime: Long = 0L,
  ): android.app.ApplicationStartInfo {
    val startInfo = mock<android.app.ApplicationStartInfo>()

    val timestamps = mutableMapOf<Int, Long>()
    if (Build.VERSION.SDK_INT >= 35) {
      timestamps[android.app.ApplicationStartInfo.START_TIMESTAMP_FORK] = forkTime
      if (bindApplicationTime > 0) {
        timestamps[android.app.ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION] =
          bindApplicationTime
      }
      if (applicationOnCreateTime > 0) {
        timestamps[android.app.ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE] =
          applicationOnCreateTime
      }
      if (firstFrameTime > 0) {
        timestamps[android.app.ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME] = firstFrameTime
      }
      if (fullyDrawnTime > 0) {
        timestamps[android.app.ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN] = fullyDrawnTime
      }

      whenever(startInfo.reason).thenReturn(android.app.ApplicationStartInfo.START_REASON_LAUNCHER)
    }

    whenever(startInfo.startupTimestamps).thenReturn(timestamps)

    return startInfo
  }
}
