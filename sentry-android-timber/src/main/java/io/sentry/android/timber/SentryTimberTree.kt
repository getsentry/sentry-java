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

    /** Log a verbose message with optional format args. */
    override fun v(
        message: String?,
        vararg args: Any?
    ) {
        logWithSentry(Log.VERBOSE, null, message, *args)
    }

    /** Log a verbose exception and a message with optional format args. */
    override fun v(
        t: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        logWithSentry(Log.VERBOSE, t, message, *args)
    }

    /** Log a verbose exception. */
    override fun v(t: Throwable?) {
        logWithSentry(Log.VERBOSE, t, null)
    }

    /** Log a debug message with optional format args. */
    override fun d(
        message: String?,
        vararg args: Any?
    ) {
        logWithSentry(Log.DEBUG, null, message, *args)
    }

    /** Log a debug exception and a message with optional format args. */
    override fun d(
        t: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        logWithSentry(Log.DEBUG, t, message, *args)
    }

    /** Log a debug exception. */
    override fun d(t: Throwable?) {
        logWithSentry(Log.DEBUG, t, null)
    }

    /** Log an info message with optional format args. */
    override fun i(
        message: String?,
        vararg args: Any?
    ) {
        logWithSentry(Log.INFO, null, message, *args)
    }

    /** Log an info exception and a message with optional format args. */
    override fun i(
        t: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        logWithSentry(Log.INFO, t, message, *args)
    }

    /** Log an info exception. */
    override fun i(t: Throwable?) {
        logWithSentry(Log.INFO, t, null)
    }

    /** Log a warning message with optional format args. */
    override fun w(
        message: String?,
        vararg args: Any?
    ) {
        logWithSentry(Log.WARN, null, message, *args)
    }

    /** Log a warning exception and a message with optional format args. */
    override fun w(
        t: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        logWithSentry(Log.WARN, t, message, *args)
    }

    /** Log a warning exception. */
    override fun w(t: Throwable?) {
        logWithSentry(Log.WARN, t, null)
    }

    /** Log an error message with optional format args. */
    override fun e(
        message: String?,
        vararg args: Any?
    ) {
        logWithSentry(Log.ERROR, null, message, *args)
    }

    /** Log an error exception and a message with optional format args. */
    override fun e(
        t: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        logWithSentry(Log.ERROR, t, message, *args)
    }

    /** Log an error exception. */
    override fun e(t: Throwable?) {
        logWithSentry(Log.ERROR, t, null)
    }

    /** Log an assert message with optional format args. */
    override fun wtf(
        message: String?,
        vararg args: Any?
    ) {
        logWithSentry(Log.ASSERT, null, message, *args)
    }

    /** Log an assert exception and a message with optional format args. */
    override fun wtf(
        t: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        logWithSentry(Log.ASSERT, t, message, *args)
    }

    /** Log an assert exception. */
    override fun wtf(t: Throwable?) {
        logWithSentry(Log.ASSERT, t, null)
    }

    /** Log at `priority` a message with optional format args. */
    override fun log(
        priority: Int,
        message: String?,
        vararg args: Any?
    ) {
        logWithSentry(priority, null, message, *args)
    }

    /** Log at `priority` an exception and a message with optional format args. */
    override fun log(
        priority: Int,
        t: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        logWithSentry(priority, t, message, *args)
    }

    /** Log at `priority` an exception. */
    override fun log(
        priority: Int,
        t: Throwable?
    ) {
        logWithSentry(priority, t, null)
    }

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        // no-op as we've overridden all the methods
    }

    private fun logWithSentry(
        priority: Int,
        throwable: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        if (message.isNullOrEmpty() && throwable == null) {
            return // Swallow message if it's null and there's no throwable
        }

        val level = getSentryLevel(priority)
        val sentryMessage = Message().apply {
            this.message = message
            if (message != null && args.isNotEmpty()) {
                this.formatted = message.format(*args)
            }
            this.params = args.mapNotNull { it.toString() }
        }

        addBreadcrumb(level, sentryMessage)
        captureEvent(level, sentryMessage, throwable)
    }

    /**
     * do not log if it's lower than min. required level.
     */
    private fun isLoggable(
        level: SentryLevel,
        minLevel: SentryLevel
    ): Boolean = level.ordinal >= minLevel.ordinal

    /**
     * Captures an event with the given attributes
     */
    private fun captureEvent(
        sentryLevel: SentryLevel,
        msg: Message,
        throwable: Throwable?
    ) {
        if (isLoggable(sentryLevel, minEventLevel)) {
            val sentryEvent = SentryEvent().apply {
                level = sentryLevel
                throwable?.let { setThrowable(it) }
                message = msg
                logger = "Timber"
            }

            hub.captureEvent(sentryEvent)
        }
    }

    /**
     * Adds a breadcrumb
     */
    private fun addBreadcrumb(
        sentryLevel: SentryLevel,
        msg: Message
    ) {
        // checks the breadcrumb level
        if (isLoggable(sentryLevel, minBreadcrumbLevel)) {
            val breadCrumb = Breadcrumb().apply {
                level = sentryLevel
                category = "Timber"
                message = msg.message
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
