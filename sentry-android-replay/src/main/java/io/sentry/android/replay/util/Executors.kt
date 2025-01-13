package io.sentry.android.replay.util

import io.sentry.ISentryExecutorService
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS

internal fun ExecutorService.gracefullyShutdown(options: SentryOptions) {
    synchronized(this) {
        if (!isShutdown) {
            shutdown()
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

internal fun ISentryExecutorService.submitSafely(
    options: SentryOptions,
    taskName: String,
    task: Runnable
): Future<*>? {
    return try {
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
}

internal fun ExecutorService.submitSafely(
    options: SentryOptions,
    taskName: String,
    task: Runnable
): Future<*>? {
    if (Thread.currentThread().name.startsWith("SentryReplayIntegration")) {
        // we're already on the worker thread, no need to submit
        task.run()
        return null
    }
    return try {
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
}

internal fun ScheduledExecutorService.scheduleAtFixedRateSafely(
    options: SentryOptions,
    taskName: String,
    initialDelay: Long,
    period: Long,
    unit: TimeUnit,
    task: Runnable
): ScheduledFuture<*>? {
    return try {
        scheduleAtFixedRate({
            try {
                task.run()
            } catch (e: Throwable) {
                options.logger.log(ERROR, "Failed to execute task $taskName", e)
            }
        }, initialDelay, period, unit)
    } catch (e: Throwable) {
        options.logger.log(ERROR, "Failed to submit task $taskName to executor", e)
        null
    }
}
