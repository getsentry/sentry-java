package io.sentry.android.replay.util

import io.sentry.SentryLevel.ERROR
import io.sentry.SentryOptions
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * An ExecutorService which is safe in terms of submitting tasks - it won't crash but will swallow
 * and log them.
 */
internal class ReplayExecutorService(
  private val delegate: ScheduledExecutorService,
  private val options: SentryOptions,
) : ScheduledExecutorService by delegate {
  override fun submit(task: Runnable): Future<*>? {
    if (Thread.currentThread().name.startsWith("SentryReplayIntegration")) {
      // we're already on the worker thread, no need to submit
      task.run()
      return null
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
}

internal class ReplayRunnable(val taskName: String, delegate: Runnable) : Runnable by delegate
