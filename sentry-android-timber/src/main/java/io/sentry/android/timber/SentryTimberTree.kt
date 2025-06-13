@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.sentry.android.timber

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import timber.log.Timber

/**
 * Sentry Timber tree which is responsible to capture events via Timber
 */
@Suppress("TooManyFunctions") // we have to override all methods to be able to tweak logging
public class SentryTimberTree(
    private val scopes: IScopes,
    private val minEventLevel: SentryLevel,
    private val minBreadcrumbLevel: SentryLevel
) : Timber.Tree() {

    /** Log a verbose message with optional format args. */
    override fun v(
        message: String?,
        vararg args: Any?
    ) {
        val tag = explicitTag.get()
        super.v(message, *args)
        logWithSentry(Log.VERBOSE, null, message, tag, *args)
    }

    /** Log a verbose exception and a message with optional format args. */
    override fun v(
        t: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        val tag = explicitTag.get()
        super.v(t, message, *args)
        logWithSentry(Log.VERBOSE, t, message, tag, *args)
    }

    /** Log a verbose exception. */
    override fun v(t: Throwable?) {
        val tag = explicitTag.get()
        super.v(t)
        logWithSentry(Log.VERBOSE, t, null, tag, null)
    }

    /** Log a debug message with optional format args. */
    override fun d(
        message: String?,
        vararg args: Any?
    ) {
        val tag = explicitTag.get()
        super.d(message, *args)
        logWithSentry(Log.DEBUG, null, message, tag, *args)
    }

    /** Log a debug exception and a message with optional format args. */
    override fun d(
        t: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        val tag = explicitTag.get()
        super.d(t, message, *args)
        logWithSentry(Log.DEBUG, t, message, tag, *args)
    }

    /** Log a debug exception. */
    override fun d(t: Throwable?) {
        val tag = explicitTag.get()
        super.d(t)
        logWithSentry(Log.DEBUG, t, null, tag, null)
    }

    /** Log an info message with optional format args. */
    override fun i(
        message: String?,
        vararg args: Any?
    ) {
        val tag = explicitTag.get()
        super.d(message, *args)
        logWithSentry(Log.INFO, null, message, tag, *args)
    }

    /** Log an info exception and a message with optional format args. */
    override fun i(
        t: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        val tag = explicitTag.get()
        super.i(t, message, *args)
        logWithSentry(Log.INFO, t, message, tag, *args)
    }

    /** Log an info exception. */
    override fun i(t: Throwable?) {
        val tag = explicitTag.get()
        super.i(t)
        logWithSentry(Log.INFO, t, null, tag, null)
    }

    /** Log a warning message with optional format args. */
    override fun w(
        message: String?,
        vararg args: Any?
    ) {
        val tag = explicitTag.get()
        super.w(message, *args)
        logWithSentry(Log.WARN, null, message, tag, *args)
    }

    /** Log a warning exception and a message with optional format args. */
    override fun w(
        t: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        val tag = explicitTag.get()
        super.w(t, message, *args)
        logWithSentry(Log.WARN, t, message, tag, *args)
    }

    /** Log a warning exception. */
    override fun w(t: Throwable?) {
        val tag = explicitTag.get()
        super.w(t)
        logWithSentry(Log.WARN, t, null, tag, null)
    }

    /** Log an error message with optional format args. */
    override fun e(
        message: String?,
        vararg args: Any?
    ) {
        val tag = explicitTag.get()
        super.e(message, *args)
        logWithSentry(Log.ERROR, null, message, tag, *args)
    }

    /** Log an error exception and a message with optional format args. */
    override fun e(
        t: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        val tag = explicitTag.get()
        super.e(t, message, *args)
        logWithSentry(Log.ERROR, t, message, tag, *args)
    }

    /** Log an error exception. */
    override fun e(t: Throwable?) {
        val tag = explicitTag.get()
        super.e(t)
        logWithSentry(Log.ERROR, t, null, tag, null)
    }

    /** Log an assert message with optional format args. */
    override fun wtf(
        message: String?,
        vararg args: Any?
    ) {
        val tag = explicitTag.get()
        super.wtf(message, *args)
        logWithSentry(Log.ASSERT, null, message, tag, *args)
    }

    /** Log an assert exception and a message with optional format args. */
    override fun wtf(
        t: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        val tag = explicitTag.get()
        super.wtf(t, message, *args)
        logWithSentry(Log.ASSERT, t, message, tag, *args)
    }

    /** Log an assert exception. */
    override fun wtf(t: Throwable?) {
        val tag = explicitTag.get()
        super.wtf(t)
        logWithSentry(Log.ASSERT, t, null, tag, null)
    }

    /** Log at `priority` a message with optional format args. */
    override fun log(
        priority: Int,
        message: String?,
        vararg args: Any?
    ) {
        val tag = explicitTag.get()
        super.log(priority, message, *args)
        logWithSentry(priority, null, message, tag, *args)
    }

    /** Log at `priority` an exception and a message with optional format args. */
    override fun log(
        priority: Int,
        t: Throwable?,
        message: String?,
        vararg args: Any?
    ) {
        val tag = explicitTag.get()
        super.log(priority, t, message, *args)
        logWithSentry(priority, t, message, tag, *args)
    }

    /** Log at `priority` an exception. */
    override fun log(
        priority: Int,
        t: Throwable?
    ) {
        super.log(priority, t)
        logWithSentry(priority, t, null, tag, null)
    }

    private fun logWithSentry(
        priority: Int,
        throwable: Throwable?,
        message: String?,
        tag: String?,
        vararg args: Any?
    ) {
        if (message.isNullOrEmpty() && throwable == null) {
            return // Swallow message if it's null and there's no throwable
        }

        val level = getSentryLevel(priority)
        val sentryMessage = Message().apply {
            this.message = message
            if (!message.isNullOrEmpty() && args.isNotEmpty()) {
                this.formatted = message.format(*args)
            }
            this.params = args.map { it.toString() }
        }

        captureEvent(level, tag, sentryMessage, throwable)
        addBreadcrumb(level, sentryMessage, tag, throwable)
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
        tag: String?,
        msg: Message,
        throwable: Throwable?
    ) {
        if (isLoggable(sentryLevel, minEventLevel)) {
            val sentryEvent = SentryEvent().apply {
                level = sentryLevel
                throwable?.let { setThrowable(it) }
                tag?.let {
                    setTag("TimberTag", it)
                }
                message = msg
                logger = "Timber"
            }

            scopes.captureEvent(sentryEvent)
        }
    }

    /**
     * Adds a breadcrumb
     */
    private fun addBreadcrumb(
        sentryLevel: SentryLevel,
        msg: Message,
        tag: String?,
        throwable: Throwable?
    ) {
        // checks the breadcrumb level
        if (isLoggable(sentryLevel, minBreadcrumbLevel)) {
            val throwableMsg = throwable?.message
            val breadCrumb = when {
                msg.message != null -> Breadcrumb().apply {
                    level = sentryLevel
                    category = "Timber"
                    message = msg.formatted ?: msg.message
                    tag?.let { t ->
                        setData("tag", t)
                    }
                }
                throwableMsg != null -> Breadcrumb.error(throwableMsg).apply {
                    category = "exception"
                }
                else -> null
            }

            breadCrumb?.let { scopes.addBreadcrumb(it) }
        }
    }

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        // no-op
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
