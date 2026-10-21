package io.sentry.android.replay.util

import io.sentry.SentryLevel.ERROR
import io.sentry.SentryOptions
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * An ExecutorService which is safe in terms of submitting tasks - it won't crash but will swallow
 * and log them.
 */
internal class ReplayExecutorService(
  private val delegate: ScheduledExecutorService,
  private val options: SentryOptions,
) : ScheduledExecutorService by delegate {
  /**
   * Submits [task] for execution and returns a [Future] describing what happened. The return value
   * has three distinct outcomes callers can rely on:
   * - [CompletedFuture] — the caller is already on the replay worker thread, so the task was run
   *   inline before this method returned. Skips the queue.
   * - A regular [Future] from the underlying [ScheduledExecutorService] — the task was queued and
   *   will run asynchronously.
   * - `null` — the underlying executor rejected the submission (typically because it has been shut
   *   down). The task did NOT run; callers that need cleanup must handle it themselves.
   */
  override fun submit(task: Runnable): Future<*>? {
    if (Thread.currentThread().name.startsWith("SentryReplayIntegration")) {
      task.run()
      return CompletedFuture
    }
    return try {
      delegate.submit {
        try {
          task.run()
        } catch (e: Throwable) {
          options.logger.log(
            ERROR,
            "Failed to execute task ${if (task is ReplayRunnable) task.taskName else ""}",
            e,
          )
        }
      }
    } catch (e: Throwable) {
      options.logger.log(
        ERROR,
        "Failed to submit task ${if (task is ReplayRunnable) task.taskName else ""} to executor",
        e,
      )
      null
    }
  }

  override fun shutdown() {
    synchronized(this) {
      if (!isShutdown) {
        delegate.shutdown()
      }
      try {
        if (!awaitTermination(options.shutdownTimeoutMillis, MILLISECONDS)) {
          shutdownNow()
        }
      } catch (e: InterruptedException) {
        shutdownNow()
        Thread.currentThread().interrupt()
      }
    }
  }

  fun gracefulShutdown() {
    synchronized(this) {
      if (!isShutdown) {
        delegate.shutdown()
      }
    }
  }
}

internal class ReplayRunnable(val taskName: String, delegate: Runnable) : Runnable by delegate

/** A Future that represents an already-completed inline execution — never used as null. */
internal object CompletedFuture : Future<Unit> {
  override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

  override fun isCancelled(): Boolean = false

  override fun isDone(): Boolean = true

  override fun get() {}

  override fun get(timeout: Long, unit: TimeUnit) {}
}
