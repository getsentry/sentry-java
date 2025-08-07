package io.sentry.test

import io.sentry.IScope
import io.sentry.ISentryClient
import io.sentry.ISentryExecutorService
import io.sentry.Scope
import io.sentry.Scopes
import io.sentry.SentryOptions
import io.sentry.backpressure.IBackpressureMonitor
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ImmediateExecutorService : ISentryExecutorService {
  override fun submit(runnable: Runnable): Future<*> {
    if (runnable !is IBackpressureMonitor) {
      runnable.run()
    }
    return mock()
  }

  override fun <T> submit(callable: Callable<T>): Future<T> = mock()

  override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> {
    if (runnable !is IBackpressureMonitor) {
      runnable.run()
    }
    return mock<Future<Runnable>>()
  }

  override fun close(timeoutMillis: Long) {}

  override fun isClosed(): Boolean = false

  override fun prewarm() = Unit
}

class DeferredExecutorService : ISentryExecutorService {
  private var runnables = ArrayList<Runnable>()
  private var scheduledRunnables = ArrayList<Runnable>()

  fun runAll() {
    // take a snapshot of the runnable list in case
    // executing the runnable itself schedules more runnables
    val currentRunnableList = runnables
    val currentScheduledRunnableList = scheduledRunnables

    synchronized(this) {
      runnables = ArrayList()
      scheduledRunnables = ArrayList()
    }

    currentRunnableList.forEach { it.run() }
    currentScheduledRunnableList.forEach { it.run() }
  }

  override fun submit(runnable: Runnable): Future<*> {
    synchronized(this) { runnables.add(runnable) }
    return FutureTask {}
  }

  override fun <T> submit(callable: Callable<T>): Future<T> = mock()

  override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> {
    synchronized(this) { scheduledRunnables.add(runnable) }
    return FutureTask {}
  }

  override fun close(timeoutMillis: Long) {}

  override fun isClosed(): Boolean = false

  override fun prewarm() = Unit

  fun hasScheduledRunnables(): Boolean = scheduledRunnables.isNotEmpty()
}

fun createSentryClientMock(enabled: Boolean = true) =
  mock<ISentryClient>().also {
    val isEnabled = AtomicBoolean(enabled)
    whenever(it.isEnabled).then { isEnabled.get() }
    whenever(it.close()).then { isEnabled.set(false) }
    whenever(it.close(any())).then { isEnabled.set(false) }
  }

fun createTestScopes(
  options: SentryOptions? = null,
  enabled: Boolean = true,
  scope: IScope? = null,
  isolationScope: IScope? = null,
  globalScope: IScope? = null,
): Scopes {
  val optionsToUse = options ?: SentryOptions().also { it.dsn = "https://key@sentry.io/proj" }
  initForTest(optionsToUse)
  val scopeToUse = scope ?: Scope(optionsToUse)
  val isolationScopeToUse = isolationScope ?: Scope(optionsToUse)
  val globalScopeToUse = globalScope ?: Scope(optionsToUse)
  return Scopes(scopeToUse, isolationScopeToUse, globalScopeToUse, "test").also {
    if (enabled) {
      it.bindClient(createSentryClientMock())
    }
  }
}
