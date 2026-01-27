package io.sentry.android.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IScopes
import io.sentry.ISentryExecutorService
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.TransactionContext
import java.util.concurrent.Callable
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
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
    options.dateProvider = mock<io.sentry.SentryDateProvider>()

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
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    val mockTransaction = mock<ITransaction>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)

    val startInfo = createMockApplicationStartInfo()
    listenerCaptor.firstValue.accept(startInfo)

    verify(mockTransaction).setTag(eq("start.reason"), any())
  }

  @Test
  fun `transaction includes start type from ApplicationStartInfo`() {
    val listenerCaptor = argumentCaptor<Consumer<android.app.ApplicationStartInfo>>()
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    val mockTransaction = mock<ITransaction>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)

    val startInfo = createMockApplicationStartInfo()
    whenever(startInfo.startType)
      .thenReturn(
        if (Build.VERSION.SDK_INT >= 35) android.app.ApplicationStartInfo.START_TYPE_COLD else 0
      )
    listenerCaptor.firstValue.accept(startInfo)

    verify(mockTransaction).setTag("start.type", "cold")
  }

  @Test
  fun `transaction includes launch mode from ApplicationStartInfo`() {
    val listenerCaptor = argumentCaptor<Consumer<android.app.ApplicationStartInfo>>()
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    val mockTransaction = mock<ITransaction>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)

    val startInfo = createMockApplicationStartInfo()
    whenever(startInfo.launchMode)
      .thenReturn(
        if (Build.VERSION.SDK_INT >= 35) android.app.ApplicationStartInfo.LAUNCH_MODE_STANDARD
        else 0
      )
    listenerCaptor.firstValue.accept(startInfo)

    verify(mockTransaction).setTag("start.launch_mode", "standard")
  }

  @Test
  fun `creates bind_application span when timestamp available`() {
    val listenerCaptor = argumentCaptor<Consumer<android.app.ApplicationStartInfo>>()
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    val mockTransaction = mock<ITransaction>()
    val mockSpan = mock<ISpan>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)
    whenever(
        mockTransaction.startChild(eq("app.start.bind_application"), anyOrNull(), any(), any())
      )
      .thenReturn(mockSpan)

    val startInfo =
      createMockApplicationStartInfo(forkTime = 1000000000L, bindApplicationTime = 1100000000L)
    listenerCaptor.firstValue.accept(startInfo)

    verify(mockTransaction).startChild(eq("app.start.bind_application"), anyOrNull(), any(), any())
    verify(mockSpan).finish(any(), any())
  }

  @Test
  fun `creates application_oncreate span when timestamp available`() {
    val listenerCaptor = argumentCaptor<Consumer<android.app.ApplicationStartInfo>>()
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    val mockTransaction = mock<ITransaction>()
    val mockSpan = mock<ISpan>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)
    whenever(
        mockTransaction.startChild(eq("app.start.application_oncreate"), anyOrNull(), any(), any())
      )
      .thenReturn(mockSpan)

    val startInfo =
      createMockApplicationStartInfo(forkTime = 1000000000L, applicationOnCreateTime = 1200000000L)
    listenerCaptor.firstValue.accept(startInfo)

    verify(mockTransaction)
      .startChild(eq("app.start.application_oncreate"), anyOrNull(), any(), any())
    verify(mockSpan).finish(any(), any())
  }

  @Test
  fun `creates ttid span when timestamp available`() {
    val listenerCaptor = argumentCaptor<Consumer<android.app.ApplicationStartInfo>>()
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    val mockTransaction = mock<ITransaction>()
    val mockSpan = mock<ISpan>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)
    whenever(mockTransaction.startChild(eq("app.start.ttid"), anyOrNull(), any(), any()))
      .thenReturn(mockSpan)

    val startInfo =
      createMockApplicationStartInfo(forkTime = 1000000000L, firstFrameTime = 1500000000L)
    listenerCaptor.firstValue.accept(startInfo)

    verify(mockTransaction).startChild(eq("app.start.ttid"), anyOrNull(), any(), any())
    verify(mockSpan).finish(any(), any())
  }

  @Test
  fun `creates ttfd span when timestamp available`() {
    val listenerCaptor = argumentCaptor<Consumer<android.app.ApplicationStartInfo>>()
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    val mockTransaction = mock<ITransaction>()
    val mockSpan = mock<ISpan>()
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>()))
      .thenReturn(mockTransaction)
    whenever(mockTransaction.startChild(eq("app.start.ttfd"), anyOrNull(), any(), any()))
      .thenReturn(mockSpan)

    val startInfo =
      createMockApplicationStartInfo(forkTime = 1000000000L, fullyDrawnTime = 2000000000L)
    listenerCaptor.firstValue.accept(startInfo)

    verify(mockTransaction).startChild(eq("app.start.ttfd"), anyOrNull(), any(), any())
    verify(mockSpan).finish(any(), any())
  }

  @Test
  fun `closes integration without errors`() {
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    integration.close()
    // Should not throw exception
  }

  @Test
  fun `transaction name includes reason label`() {
    val listenerCaptor = argumentCaptor<Consumer<android.app.ApplicationStartInfo>>()
    val integration = ApplicationStartInfoIntegration(context, buildInfoProvider)
    integration.register(scopes, options)

    verify(activityManager)
      .addApplicationStartInfoCompletionListener(any(), listenerCaptor.capture())

    var capturedContext: TransactionContext? = null
    whenever(scopes.startTransaction(any(), any<io.sentry.TransactionOptions>())).thenAnswer {
      capturedContext = it.arguments[0] as TransactionContext
      mock()
    }

    val startInfo = createMockApplicationStartInfo()
    whenever(startInfo.reason)
      .thenReturn(
        if (Build.VERSION.SDK_INT >= 35) android.app.ApplicationStartInfo.START_REASON_LAUNCHER
        else 0
      )
    listenerCaptor.firstValue.accept(startInfo)

    assertNotNull(capturedContext)
    assertEquals("app.start.launcher", capturedContext!!.name)
  }

  // Helper methods
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
