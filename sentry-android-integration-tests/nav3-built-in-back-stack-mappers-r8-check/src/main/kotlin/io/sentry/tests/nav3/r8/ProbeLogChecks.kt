package io.sentry.tests.nav3.r8

import android.content.Context
import io.sentry.ILogger
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import java.util.Collections

/**
 * Records the messages the SDK logs, so the check can tell whether the mapper's compatibility probe
 * ran conclusively under R8.
 */
class RecordingLogger : ILogger {

  val messages: MutableList<String> = Collections.synchronizedList(mutableListOf())

  override fun log(level: SentryLevel, message: String, vararg args: Any?) {
    messages += format(message, args)
  }

  override fun log(level: SentryLevel, message: String, throwable: Throwable?) {
    messages += withError(message, throwable)
  }

  override fun log(
    level: SentryLevel,
    throwable: Throwable?,
    message: String,
    vararg args: Any?,
  ) {
    messages += withError(format(message, args), throwable)
  }

  override fun isEnabled(level: SentryLevel?): Boolean = true

  private fun format(message: String, args: Array<out Any?>): String =
    if (args.isEmpty()) message else String.format(message, *args)

  /** Appends [throwable] and its causes, so a failed check shows why the probe failed. */
  private fun withError(message: String, throwable: Throwable?): String =
    if (throwable == null) message
    else message + generateSequence(throwable) { it.cause }.take(5).joinToString { " | $it" }
}

/**
 * Initializes Sentry with [logger] and `debug` enabled, so the mapper's logs reach it. The DSN
 * points nowhere reachable; the check captures no events.
 */
fun initSentry(context: Context, logger: ILogger) {
  SentryAndroid.init(context) { options ->
    options.dsn = "https://key@localhost/0"
    options.isDebug = true
    options.setLogger(logger)
  }
}


/**
 * Checks that the compatibility probe completed conclusively under R8.
 *
 * Names alone aren't enough: an inconclusive probe can produce correct names through per-type
 * fallback while failing to detect an incompatible kotlinx.serialization version.
 *
 * Inspects [logger]'s messages after mapping every [Expectation]. The [NotSerializable] warning
 * confirms logging works; probe detection relies on fixed message prefixes.
 */
fun probeLogChecks(logger: RecordingLogger): List<Check> {
  val messages = logger.messages.toList()
  return listOf(
    Check(
      "the mapper's warnings reach the logger (one names ${NotSerializable::class.java.name})",
      messages.any { NotSerializable::class.java.name in it },
    ),
  ) +
    PROBE_MESSAGE_PREFIXES.map { prefix ->
      val logged = messages.firstOrNull { it.startsWith(prefix) }
      Check(
        "nothing logged \"$prefix…\"" + if (logged != null) " (logged: $logged)" else "",
        logged == null,
      )
    }
}

/**
 * The prefixes of the messages the mapper logs when its compatibility probe is inconclusive or
 * fails, or a built-in mapper can't be created.
 *
 * See `KotlinxMapperDiagnostics` and `BuiltInBackStackEntryMappers` in sentry-android-navigation3.
 */
private val PROBE_MESSAGE_PREFIXES =
  listOf(
    "Nav3 couldn't verify kotlinx.serialization support",
    "Nav3 built-in mapper creation failed",
  )
