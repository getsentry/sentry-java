package io.sentry.android.replay.util

import io.sentry.ISentryExecutorService
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryOptions
import io.sentry.util.ExceptionUtils
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException

internal fun ISentryExecutorService.submitSafely(
  options: SentryOptions,
  taskName: String,
  task: Runnable,
): Future<*>? =
  try {
    submit {
      try {
        task.run()
      } catch (e: Throwable) {
        // The tasks decode frames and encode video through the device's media stack, so we can't
        // know what they throw. The failure is logged here because nobody reads the returned
        // future, and the task's work is lost.
        ExceptionUtils.rethrowIfFatal(e)
        options.logger.log(ERROR, "Failed to execute task $taskName", e)
      }
    }
  } catch (e: RejectedExecutionException) {
    options.logger.log(ERROR, "Failed to submit task $taskName to executor", e)
    null
  }
