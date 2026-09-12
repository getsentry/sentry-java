package io.sentry.compose.navigation3

import io.sentry.ILogger
import io.sentry.SentryLevel.WARNING
import io.sentry.util.ExceptionUtils
import java.util.IdentityHashMap

/** Translates app-defined back stack entries into displayable [RouteEntry]s. */
internal class RouteTranslator<T : Any>(
  private val resolvers: () -> RouteResolvers<T>,
  private val maxCapturedBackStackEntries: Int,
  private val logger: ILogger,
) {

  internal class UpdateWarningState {
    private var hasLoggedUnsupportedValueWarning = false

    fun logUnsupportedValueWarning(typeName: String?, logger: ILogger) {
      if (hasLoggedUnsupportedValueWarning) {
        return
      }

      logger.log(
        WARNING,
        "Nav3 argumentsExtractor returned unsupported value of type %s while processing this back " +
          "stack update. Falling back to toString(). Use String, CharSequence, Char, Number, " +
          "Boolean, Enum, Map, Collection, object Array, and primitive array values for reliable " +
          "results.",
        typeName,
      )
      hasLoggedUnsupportedValueWarning = true
    }
  }

  companion object {

    /**
     * Max nesting depth allowed while sanitizing a single argument value for a given back stack
     * entry.
     *
     * If exceeded, all arguments for that back stack entry are dropped.
     */
    private const val MAX_ARGUMENT_DEPTH = 20

    /**
     * Max number of argument values visited while sanitizing all entries in a given back stack
     * update.
     *
     * If exceeded, the entry that overflows loses its arguments, as do older entries; newer entries
     * are preserved. E.g., suppose we have the following back stack:
     *
     * - /Checkout -> Top of the stack and processed first
     * - /ProductDetail -> Processed second and overflows the `MAX_ARGUMENT_VALUES` budget
     * - /Home
     *
     * Then /ProductDetail and /Home will have no arguments, but /Checkout will.
     */
    private const val MAX_ARGUMENT_VALUES = 1_000
  }

  /** Converts the provided [backStack] into a serializable list of [RouteEntry]s. */
  fun toRouteEntries(
    backStack: List<T>,
    updateWarningState: UpdateWarningState = UpdateWarningState(),
  ): List<RouteEntry> {
    val state = ArgumentSanitizationState()

    return backStack.takeLast(maxCapturedBackStackEntries).asReversed().map { entry ->
      buildMap {
        put("route", resolveRouteName(entry))

        val args =
          if (state.isValueBudgetExceeded()) {
            emptyMap()
          } else {
            resolveArguments(entry, state, updateWarningState)
          }
        if (args.isNotEmpty()) {
          put("args", args)
        }
      }
    }
  }

  /**
   * Returns a route name for the provided [backStackEntry], based on this translator's
   * [name extractor][RouteResolvers.nameExtractor].
   *
   * The returned name is normalized to always include a leading slash. E.g., both `PromoDialog` and
   * `/PromoDialog` are resolved to `/PromoDialog`. (Doing so maintains parity with our Nav2
   * convention.)
   */
  @Suppress("TooGenericExceptionCaught")
  fun resolveRouteName(backStackEntry: T): String {
    val name =
      try {
        resolvers.invoke().getName(backStackEntry)
      } catch (t: Throwable) {
        ExceptionUtils.rethrowIfFatal(t)
        logger.log(
          WARNING,
          "Nav3 nameExtractor threw while resolving a route name. Falling back to class simpleName.",
          t,
        )
        null
      } ?: backStackEntry::class.simpleName ?: "unknown"

    return "/${name.removePrefix("/")}"
  }

  /**
   * Returns the arguments for the provided [backStackEntry], based on this translator's
   * [arguments extractor][RouteResolvers.argumentsExtractor].
   *
   * The arguments are sanitized before being returned, i.e., bounded in size and depth, and
   * converted into a serializable form.
   */
  @Suppress("TooGenericExceptionCaught")
  fun resolveArguments(
    backStackEntry: T,
    updateWarningState: UpdateWarningState = UpdateWarningState(),
  ): Map<String, Any?> =
    resolveArguments(backStackEntry, ArgumentSanitizationState(), updateWarningState)

  @Suppress("TooGenericExceptionCaught")
  private fun resolveArguments(
    backStackEntry: T,
    state: ArgumentSanitizationState,
    updateWarningState: UpdateWarningState,
  ): Map<String, Any?> {
    val raw =
      try {
        resolvers.invoke().getArguments(backStackEntry) ?: return emptyMap()
      } catch (t: Throwable) {
        ExceptionUtils.rethrowIfFatal(t)
        logger.log(
          WARNING,
          "Nav3 argumentsExtractor threw while resolving arguments. Skipping arguments.",
          t,
        )
        return emptyMap()
      }

    return try {
      sanitizeArguments(raw, state, updateWarningState)
    } catch (_: ArgumentValueBudgetExceededException) {
      logger.log(
        WARNING,
        "Nav3 arguments exceeded the maximum total value count for one backstack update. " +
          "Skipping arguments for this and older captured entries.",
      )
      emptyMap()
    } catch (_: ArgumentStructureException) {
      logger.log(
        WARNING,
        "Nav3 argument sanitization failed (possibly a cyclic or deeply nested structure). " +
          "Skipping arguments.",
      )
      emptyMap()
    } catch (t: Throwable) {
      ExceptionUtils.rethrowIfFatal(t)
      logger.log(
        WARNING,
        "Nav3 argument sanitization failed (possibly a cyclic or deeply nested structure). " +
          "Skipping arguments.",
        t,
      )
      emptyMap()
    }
  }

  private fun sanitizeArguments(
    args: Map<String, Any?>,
    state: ArgumentSanitizationState,
    updateWarningState: UpdateWarningState,
  ): Map<String, Any?> = sanitizeMap(args, state, depth = 0, updateWarningState)

  private fun sanitizeMap(
    value: Map<*, *>,
    state: ArgumentSanitizationState,
    depth: Int,
    updateWarningState: UpdateWarningState,
  ): Map<String, Any?> {
    state.enter(value)
    try {
      val sanitized = LinkedHashMap<String, Any?>()
      for ((key, childValue) in value) {
        sanitized[key.toString()] = sanitizeValue(childValue, state, depth + 1, updateWarningState)
      }
      return sanitized
    } finally {
      state.exit(value)
    }
  }

  private fun sanitizeCollection(
    value: Collection<*>,
    state: ArgumentSanitizationState,
    depth: Int,
    updateWarningState: UpdateWarningState,
  ): List<Any?> {
    state.enter(value)
    try {
      val sanitized = ArrayList<Any?>()
      for (childValue in value) {
        sanitized += sanitizeValue(childValue, state, depth + 1, updateWarningState)
      }
      return sanitized
    } finally {
      state.exit(value)
    }
  }

  private fun sanitizeValue(
    value: Any?,
    state: ArgumentSanitizationState,
    depth: Int,
    updateWarningState: UpdateWarningState,
  ): Any? {
    state.visit(depth)
    val collection = value?.asSanitizableCollectionOrNull()

    return when {
      value == null || value is String || value is Number || value is Boolean -> value
      value is CharSequence || value is Char -> value.toString()
      value is Enum<*> -> value.name
      value is Map<*, *> -> sanitizeMap(value, state, depth, updateWarningState)
      collection != null -> sanitizeCollection(collection, state, depth, updateWarningState)
      else -> {
        updateWarningState.logUnsupportedValueWarning(value::class.simpleName, logger)
        value.toString()
      }
    }
  }

  private fun Any.asSanitizableCollectionOrNull(): Collection<*>? =
    when (this) {
      is Collection<*> -> this
      is Array<*> -> asList()
      is BooleanArray -> asList()
      is ByteArray -> asList()
      is ShortArray -> asList()
      is IntArray -> asList()
      is LongArray -> asList()
      is FloatArray -> asList()
      is DoubleArray -> asList()
      is CharArray -> asList()
      else -> null
    }

  private class ArgumentSanitizationState {

    private val activeContainers = IdentityHashMap<Any, Unit>()
    private var valueCount = 0
    private var valueBudgetExceeded = false

    fun visit(depth: Int) {
      if (depth > MAX_ARGUMENT_DEPTH) {
        throw ArgumentStructureException("Nav3 arguments exceed the maximum depth")
      }
      if (++valueCount > MAX_ARGUMENT_VALUES) {
        valueBudgetExceeded = true
        throw ArgumentValueBudgetExceededException(
          "Nav3 arguments exceed the maximum total value count for one backstack update"
        )
      }
    }

    fun enter(container: Any) {
      if (activeContainers.put(container, Unit) != null) {
        throw ArgumentStructureException("Nav3 arguments contain a cyclic reference")
      }
    }

    fun exit(container: Any) {
      activeContainers.remove(container)
    }

    fun isValueBudgetExceeded(): Boolean = valueBudgetExceeded
  }

  private open class ArgumentSanitizationException(message: String) :
    IllegalArgumentException(message)

  private class ArgumentStructureException(message: String) : ArgumentSanitizationException(message)

  private class ArgumentValueBudgetExceededException(message: String) :
    ArgumentSanitizationException(message)
}

/**
 * A map consisting of a single route <> route name pair, and zero or more argument pairs.
 *
 * E.g., in serialized form:
 * ```
 *  {
 *    "route": "/ProductScreen"
 *    "args": {
 *      "product_id": 12345
 *      "promo_id:": "spring-marketing-drive-2026"
 *    }
 *  }
 * ```
 *
 * By convention, all route names are normalized to include a leading slash, and all arguments are
 * sanitized (i.e., bounded in size and depth, and converted into a serializable form).
 */
internal typealias RouteEntry = Map<String, Any?>
