package io.sentry.tests.nav3.r8

import android.content.Context
import io.sentry.ILogger
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import java.util.Collections

/**
 * Records the messages the SDK logs, so the check can tell whether the mappers' compatibility
 * probes ran conclusively under R8.
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
 * Initializes Sentry with [logger] and `debug` enabled, so the mappers' logs reach it. The DSN
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
 * Checks that both mappers' compatibility probes ran conclusively under R8, from the messages
 * [logger] recorded after the mappers mapped every [Expectation].
 *
 * The names and arguments already show whether a probe failed, but not whether the name lookup
 * probe was inconclusive, e.g., because R8 removed what looking up its own fixture needs. The
 * mappers then handle failures one type at a time, so names still come out right, but the probe
 * can't detect an incompatible kotlinx.serialization version in minified apps.
 *
 * The probes' messages are matched by their fixed prefixes. A positive control, the warning for
 * [NotSerializable], shows the logger receives the mappers' messages, so a probe message that
 * changes its prefix is the only way these checks could pass vacuously.
 */
fun probeLogChecks(logger: RecordingLogger): List<Check> {
  val messages = logger.messages.toList()
  return listOf(
    Check(
      "the mappers' warnings reach the logger (one names ${NotSerializable::class.java.name})",
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
 * The prefixes of the messages the mappers log when a compatibility probe is inconclusive or fails,
 * or a built-in mapper can't be created. See `KotlinxMapperDiagnostics` and
 * `BuiltInBackStackEntryMappers` in sentry-android-navigation3.
 */
private val PROBE_MESSAGE_PREFIXES =
  listOf(
    "Nav3 couldn't verify kotlinx.serialization support",
    "Nav3 SerialNameAndProperties is incompatible",
    "Nav3 built-in mapper creation failed",
  )
