package io.sentry.android.timber

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import timber.log.Timber

/**
 * Sentry Timber tree which is responsible to capture events via Timber
 */
class SentryTimberTree(
    private val hub: IHub,
    private val minEventLevel: SentryLevel,
    private val minBreadcrumbLevel: SentryLevel
) : Timber.Tree() {

    /**
     * do not log if it's lower than min. required level.
     */
    private fun isLoggable(level: SentryLevel, minLevel: SentryLevel): Boolean = level.ordinal >= minLevel.ordinal

    /**
     * Captures a Sentry Event if the min. level is equal or higher than the min. required level.
     */
    override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        val level = getSentryLevel(priority)

        captureEvent(level, tag, message, throwable)
        addBreadcrumb(level, message)
    }

    /**
     * Captures an event with the given attributes
     */
    private fun captureEvent(sentryLevel: SentryLevel, tag: String?, msg: String, throwable: Throwable?) {
        if (isLoggable(sentryLevel, minEventLevel)) {
            val sentryEvent = SentryEvent().apply {

                level = sentryLevel

                throwable?.let {
                    setThrowable(it)
                }

                message = Message().apply {
                    formatted = msg
                }

                tag?.let {
                    setTag("TimberTag", it)
                }

                logger = "Timber"
            }

            hub.captureEvent(sentryEvent)
        }
    }

    /**
     * Adds a breadcrumb
     */
    private fun addBreadcrumb(sentryLevel: SentryLevel, msg: String) {
        // checks the breadcrumb level
        if (isLoggable(sentryLevel, minBreadcrumbLevel)) {
            val breadCrumb = Breadcrumb().apply {
                level = sentryLevel
                category = "Timber"
                message = msg
            }

            hub.addBreadcrumb(breadCrumb)
        }
    }

    /**
     * Converts from Timber priority to SentryLevel.
     * Fallback to SentryLevel.DEBUG.
     */
    private fun getSentryLevel(priority: Int): SentryLevel {
        return when (priority) {
            Log.ASSERT -> SentryLevel.FATAL
            Log.ERROR -> SentryLevel.ERROR
            Log.WARN -> SentryLevel.WARNING
            Log.INFO -> SentryLevel.INFO
            Log.DEBUG -> SentryLevel.DEBUG
            Log.VERBOSE -> SentryLevel.DEBUG
            else -> SentryLevel.DEBUG
        }
    }
}
