package io.sentry.android.replay.util

import io.sentry.ISentryExecutorService
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryOptions
import java.util.concurrent.Future

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
        options.logger.log(ERROR, "Failed to execute task $taskName", e)
      }
    }
  } catch (e: Throwable) {
    options.logger.log(ERROR, "Failed to submit task $taskName to executor", e)
    null
  }
