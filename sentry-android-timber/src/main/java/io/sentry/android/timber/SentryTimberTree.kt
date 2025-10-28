package io.sentry.android.timber

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.SentryAttribute
import io.sentry.SentryAttributes
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryLogLevel
import io.sentry.logger.SentryLogParameters
import io.sentry.protocol.Message
import timber.log.Timber

/** Sentry Timber tree which is responsible to capture events via Timber */
@Suppress("TooManyFunctions") // we have to override all methods to be able to tweak logging
public class SentryTimberTree(
  private val scopes: IScopes,
  private val minEventLevel: SentryLevel,
  private val minBreadcrumbLevel: SentryLevel,
  private val minLogLevel: SentryLogLevel = SentryLogLevel.INFO,
) : Timber.Tree() {
  private val pendingTag = ThreadLocal<String?>()

  private fun retrieveTag(): String? {
    val tag = pendingTag.get()
    if (tag != null) {
      this.pendingTag.remove()
    }
    return tag
  }

  /** Log a verbose message with optional format args. */
  override fun v(message: String?, vararg args: Any?) {
    super.v(message, *args)
    logWithSentry(Log.VERBOSE, null, message, *args)
  }

  /** Log a verbose exception and a message with optional format args. */
  override fun v(t: Throwable?, message: String?, vararg args: Any?) {
    super.v(t, message, *args)
    logWithSentry(Log.VERBOSE, t, message, *args)
  }

  /** Log a verbose exception. */
  override fun v(t: Throwable?) {
    super.v(t)
    logWithSentry(Log.VERBOSE, t, null)
  }

  /** Log a debug message with optional format args. */
  override fun d(message: String?, vararg args: Any?) {
    super.d(message, *args)
    logWithSentry(Log.DEBUG, null, message, *args)
  }

  /** Log a debug exception and a message with optional format args. */
  override fun d(t: Throwable?, message: String?, vararg args: Any?) {
    super.d(t, message, *args)
    logWithSentry(Log.DEBUG, t, message, *args)
  }

  /** Log a debug exception. */
  override fun d(t: Throwable?) {
    super.d(t)
    logWithSentry(Log.DEBUG, t, null)
  }

  /** Log an info message with optional format args. */
  override fun i(message: String?, vararg args: Any?) {
    super.d(message, *args)
    logWithSentry(Log.INFO, null, message, *args)
  }

  /** Log an info exception and a message with optional format args. */
  override fun i(t: Throwable?, message: String?, vararg args: Any?) {
    super.i(t, message, *args)
    logWithSentry(Log.INFO, t, message, *args)
  }

  /** Log an info exception. */
  override fun i(t: Throwable?) {
    super.i(t)
    logWithSentry(Log.INFO, t, null)
  }

  /** Log a warning message with optional format args. */
  override fun w(message: String?, vararg args: Any?) {
    super.w(message, *args)
    logWithSentry(Log.WARN, null, message, *args)
  }

  /** Log a warning exception and a message with optional format args. */
  override fun w(t: Throwable?, message: String?, vararg args: Any?) {
    super.w(t, message, *args)
    logWithSentry(Log.WARN, t, message, *args)
  }

  /** Log a warning exception. */
  override fun w(t: Throwable?) {
    super.w(t)
    logWithSentry(Log.WARN, t, null)
  }

  /** Log an error message with optional format args. */
  override fun e(message: String?, vararg args: Any?) {
    super.e(message, *args)
    logWithSentry(Log.ERROR, null, message, *args)
  }

  /** Log an error exception and a message with optional format args. */
  override fun e(t: Throwable?, message: String?, vararg args: Any?) {
    super.e(t, message, *args)
    logWithSentry(Log.ERROR, t, message, *args)
  }

  /** Log an error exception. */
  override fun e(t: Throwable?) {
    super.e(t)
    logWithSentry(Log.ERROR, t, null)
  }

  /** Log an assert message with optional format args. */
  override fun wtf(message: String?, vararg args: Any?) {
    super.wtf(message, *args)
    logWithSentry(Log.ASSERT, null, message, *args)
  }

  /** Log an assert exception and a message with optional format args. */
  override fun wtf(t: Throwable?, message: String?, vararg args: Any?) {
    super.wtf(t, message, *args)
    logWithSentry(Log.ASSERT, t, message, *args)
  }

  /** Log an assert exception. */
  override fun wtf(t: Throwable?) {
    super.wtf(t)
    logWithSentry(Log.ASSERT, t, null)
  }

  /** Log at `priority` a message with optional format args. */
  override fun log(priority: Int, message: String?, vararg args: Any?) {
    super.log(priority, message, *args)
    logWithSentry(priority, null, message, *args)
  }

  /** Log at `priority` an exception and a message with optional format args. */
  override fun log(priority: Int, t: Throwable?, message: String?, vararg args: Any?) {
    super.log(priority, t, message, *args)
    logWithSentry(priority, t, message, *args)
  }

  /** Log at `priority` an exception. */
  override fun log(priority: Int, t: Throwable?) {
    super.log(priority, t)
    logWithSentry(priority, t, null)
  }

  override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
    pendingTag.set(tag)
  }

  private fun logWithSentry(
    priority: Int,
    throwable: Throwable?,
    message: String?,
    vararg args: Any?,
  ) {
    val tag = retrieveTag()

    if (message.isNullOrEmpty() && throwable == null) {
      return // Swallow message if it's null and there's no throwable
    }

    val level = getSentryLevel(priority)
    val logLevel = getSentryLogLevel(priority)
    val sentryMessage =
      Message().apply {
        this.message = message
        if (!message.isNullOrEmpty() && args.isNotEmpty()) {
          this.formatted = message.format(*args)
        }
        this.params = args.map { it.toString() }
      }

    captureEvent(level, tag, sentryMessage, throwable)
    addBreadcrumb(level, sentryMessage, throwable)
    addLog(logLevel, message, tag, throwable, *args)
  }

  /** do not log if it's lower than min. required level. */
  private fun isLoggable(level: SentryLevel, minLevel: SentryLevel): Boolean =
    level.ordinal >= minLevel.ordinal

  /** do not log if it's lower than min. required level. */
  private fun isLoggable(level: SentryLogLevel, minLevel: SentryLogLevel): Boolean =
    level.ordinal >= minLevel.ordinal

  /** Captures an event with the given attributes */
  private fun captureEvent(
    sentryLevel: SentryLevel,
    tag: String?,
    msg: Message,
    throwable: Throwable?,
  ) {
    if (isLoggable(sentryLevel, minEventLevel)) {
      val sentryEvent =
        SentryEvent().apply {
          level = sentryLevel
          throwable?.let { setThrowable(it) }
          tag?.let { setTag("TimberTag", it) }
          message = msg
          logger = "Timber"
        }

      scopes.captureEvent(sentryEvent)
    }
  }

  /** Adds a breadcrumb */
  private fun addBreadcrumb(sentryLevel: SentryLevel, msg: Message, throwable: Throwable?) {
    // checks the breadcrumb level
    if (isLoggable(sentryLevel, minBreadcrumbLevel)) {
      val throwableMsg = throwable?.message
      val breadCrumb =
        when {
          msg.message != null ->
            Breadcrumb().apply {
              level = sentryLevel
              category = "Timber"
              message = msg.formatted ?: msg.message
            }
          throwableMsg != null -> Breadcrumb.error(throwableMsg).apply { category = "exception" }
          else -> null
        }

      breadCrumb?.let { scopes.addBreadcrumb(it) }
    }
  }

  /** Send a Sentry Logs */
  private fun addLog(
    sentryLogLevel: SentryLogLevel,
    msg: String?,
    tag: String?,
    throwable: Throwable?,
    vararg args: Any?,
  ) {
    // checks the log level
    if (isLoggable(sentryLogLevel, minLogLevel)) {
      val attributes =
        tag?.let { SentryAttributes.of(SentryAttribute.stringAttribute("timber.tag", tag)) }
      val params = SentryLogParameters.create(attributes)
      params.origin = "auto.log.timber"

      val throwableMsg = throwable?.message
      when {
        msg != null && throwableMsg != null ->
          scopes.logger().log(sentryLogLevel, params, "$msg\n$throwableMsg", *args)
        msg != null -> scopes.logger().log(sentryLogLevel, params, msg, *args)
        throwableMsg != null -> scopes.logger().log(sentryLogLevel, params, throwableMsg, *args)
      }
    }
  }

  /** Converts from Timber priority to SentryLevel. Fallback to SentryLevel.DEBUG. */
  private fun getSentryLevel(priority: Int): SentryLevel =
    when (priority) {
      Log.ASSERT -> SentryLevel.FATAL
      Log.ERROR -> SentryLevel.ERROR
      Log.WARN -> SentryLevel.WARNING
      Log.INFO -> SentryLevel.INFO
      Log.DEBUG -> SentryLevel.DEBUG
      Log.VERBOSE -> SentryLevel.DEBUG
      else -> SentryLevel.DEBUG
    }

  /** Converts from Timber priority to SentryLogLevel. Fallback to SentryLogLevel.DEBUG. */
  private fun getSentryLogLevel(priority: Int): SentryLogLevel {
    return when (priority) {
      Log.ASSERT -> SentryLogLevel.FATAL
      Log.ERROR -> SentryLogLevel.ERROR
      Log.WARN -> SentryLogLevel.WARN
      Log.INFO -> SentryLogLevel.INFO
      Log.DEBUG -> SentryLogLevel.DEBUG
      Log.VERBOSE -> SentryLogLevel.TRACE
      else -> SentryLogLevel.DEBUG
    }
  }
}
