package io.sentry.android.core.internal.util

import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.ConnectivityManager
import android.os.Build
import android.telephony.TelephonyManager
import io.sentry.ISentryExecutorService
import io.sentry.SentryOptions
import io.sentry.android.core.BuildInfoProvider
import io.sentry.android.core.ContextUtils
import io.sentry.time.TestMonotonicTicker
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class ConnectionStatusProviderFixture private constructor() : AutoCloseable {
  val context = mock<Context>()
  val connectivityManager = mock<ConnectivityManager>()
  val telephonyManager = mock<TelephonyManager>()
  val options = SentryOptions()
  val provider: AndroidConnectionStatusProvider

  private val pending = ConcurrentLinkedQueue<FutureTask<Unit>>()
  private val workers = mutableListOf<Thread>()

  init {
    whenever(context.applicationContext).thenReturn(context)
    whenever(context.checkPermission(any(), any(), any())).thenReturn(PERMISSION_GRANTED)
    whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
    whenever(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(telephonyManager)
    val buildInfo = mock<BuildInfoProvider>()
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.S)
    val executor = mock<ISentryExecutorService>()
    whenever(executor.submit(any<Runnable>())).thenAnswer {
      FutureTask(it.getArgument<Runnable>(0), Unit).also(pending::add)
    }
    options.executorService = executor
    options.threadChecker = AndroidThreadChecker.getInstance()
    clearCachedAndroidServices(buildInfo)
    provider = AndroidConnectionStatusProvider(context, options, buildInfo, TestMonotonicTicker())
    options.connectionStatusProvider = provider
  }

  private fun clearCachedAndroidServices(buildInfo: BuildInfoProvider) {
    // Robolectric can reuse a static Android service cache from an earlier suite. Closing an
    // unregistered provider clears that cache so this fixture resolves its own mock services.
    val cleanupProvider =
      AndroidConnectionStatusProvider(context, options, buildInfo, TestMonotonicTicker())
    pending.clear()
    cleanupProvider.close()
    pending.remove().run()
  }

  fun runNextTask(): Future<Unit> {
    val task = checkNotNull(pending.poll()) { "Expected a queued SDK task" }
    workers +=
      thread(name = "connection-status-sdk-worker") {
        mockStatic(ContextUtils::class.java).use { contextUtils ->
          contextUtils.`when`<Boolean> { ContextUtils.isForegroundImportance() }.thenReturn(true)
          task.run()
        }
      }
    return task
  }

  override fun close() {
    workers.forEach { it.join(5000) }
    provider.close()
    while (pending.isNotEmpty()) {
      runNextTask().get(5, SECONDS)
    }
    workers.forEach { it.join(5000) }
  }

  companion object {
    fun create(): ConnectionStatusProviderFixture = ConnectionStatusProviderFixture()
  }
}

internal class PausedCall : AutoCloseable {
  private val entered = CountDownLatch(1)
  private val released = CountDownLatch(1)

  fun pause() {
    entered.countDown()
    check(released.await(10, SECONDS)) { "Paused framework call was not released" }
  }

  fun awaitEntry() {
    check(entered.await(5, SECONDS)) { "Framework call was not reached" }
  }

  override fun close() {
    released.countDown()
  }
}
