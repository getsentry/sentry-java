package io.sentry.compose.navigation3

import io.sentry.ILogger
import io.sentry.ScopesAdapter
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.NormalizedSentryBackStackEntry.Companion.UNKNOWN_ENTRY_NAME
import io.sentry.util.HttpUtils
import io.sentry.util.LoadClass
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of [BackStackEntryMapper.serialName] and
 * [BackStackEntryMapper.serialNameWithArguments].
 *
 * kotlinx.serialization is a `compileOnly` dependency. Only [KotlinxSerialNameLookup] and
 * [KotlinxArgumentsEncoder] reference it, and this class only touches the lookup after confirming
 * the library is present. That way, apps without kotlinx.serialization never load a class that
 * references it.
 *
 * Resolved names, including failed lookups, are cached per class. Route types are bounded, so the
 * cache is too.
 *
 * Arguments are encoded on every call, since they depend on the entry's values. Values whose keys
 * match the Data Collection spec's sensitive denylist are replaced with [FILTERED], at every level
 * of nesting.
 *
 * @param lookup resolves serial names and arguments, or `null` if kotlinx.serialization is
 *   unavailable.
 * @param includeArguments whether to encode each entry's properties as arguments.
 * @param logger supplies the logger at the time a warning is logged.
 */
internal class SerialNameBackStackEntryMapper(
  private val lookup: SerialNameLookup?,
  private val includeArguments: Boolean,
  private val logger: () -> ILogger,
) : BackStackEntryMapper<Any> {

  private val cache = ConcurrentHashMap<Class<*>, CachedSerialName>()
  private val typesWithUnencodableArguments =
    Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())

  override fun map(backStackEntry: Any): SentryBackStackEntry? {
    val name = serialNameOf(backStackEntry.javaClass) ?: return null
    // A resolved name implies a non-null lookup.
    val arguments = if (includeArguments) lookup?.let { argumentsOf(it, backStackEntry) } else null
    return SentryBackStackEntry(name = name, arguments = arguments)
  }

  private fun serialNameOf(type: Class<*>): String? {
    cache[type]?.let {
      return it.name
    }

    val resolved = CachedSerialName(lookup?.serialNameOrNull(type))
    val previous = cache.putIfAbsent(type, resolved)
    if (previous != null) {
      return previous.name
    }

    if (resolved.name == null) {
      logUnresolved(type)
    }
    return resolved.name
  }

  private fun argumentsOf(lookup: SerialNameLookup, backStackEntry: Any): Map<String, Any?>? {
    val encoded = lookup.argumentsOrNull(backStackEntry)
    if (encoded == null) {
      logUnencodable(backStackEntry.javaClass)
      return null
    }

    @Suppress("UNCHECKED_CAST") val filtered = filterSensitiveValues(encoded) as Map<String, Any?>
    return filtered.ifEmpty { null }
  }

  private fun logUnresolved(type: Class<*>) {
    val reason =
      if (lookup == null) {
        "kotlinx.serialization is not available"
      } else {
        "the type is not @Serializable or its serializer can't be looked up without type arguments"
      }
    logger()
      .log(
        WARNING,
        "Nav3 BackStackEntryMapper couldn't name back stack entries of type %s because %s. " +
          "Using $UNKNOWN_ENTRY_NAME instead. Make the type @Serializable or use a custom " +
          "BackStackEntryMapper.",
        type.name,
        reason,
      )
  }

  private fun logUnencodable(type: Class<*>) {
    if (!typesWithUnencodableArguments.add(type)) {
      return
    }
    logger()
      .log(
        WARNING,
        "Nav3 BackStackEntryMapper.serialNameWithArguments() couldn't encode the arguments of " +
          "back stack entries of type %s. Recording their names without arguments. Contextual " +
          "and open polymorphic properties aren't supported; use a custom BackStackEntryMapper.",
        type.name,
      )
  }

  /** Wraps a nullable name, since [ConcurrentHashMap] doesn't accept `null` values. */
  private class CachedSerialName(val name: String?)

  companion object {
    /** Replaces values whose keys match the sensitive denylist. */
    const val FILTERED: String = "[Filtered]"

    private const val KSERIALIZER_CLASS_NAME = "kotlinx.serialization.KSerializer"

    private val defaultLogger: () -> ILogger = { ScopesAdapter.getInstance().options.logger }

    private val availableLookup: SerialNameLookup? by lazy {
      if (LoadClass().isClassAvailable(KSERIALIZER_CLASS_NAME, defaultLogger())) {
        KotlinxSerialNameLookup
      } else {
        null
      }
    }

    val instance: SerialNameBackStackEntryMapper by lazy {
      SerialNameBackStackEntryMapper(availableLookup, includeArguments = false, defaultLogger)
    }

    val withArgumentsInstance: SerialNameBackStackEntryMapper by lazy {
      SerialNameBackStackEntryMapper(availableLookup, includeArguments = true, defaultLogger)
    }

    /**
     * Replaces the values of map entries whose keys match the sensitive denylist with [FILTERED],
     * recursing into nested maps and lists.
     */
    internal fun filterSensitiveValues(value: Any?): Any? =
      when (value) {
        is Map<*, *> ->
          value.entries.associate { (key, entryValue) ->
            val keyString = key.toString()
            keyString to
              if (HttpUtils.containsSensitiveDataKey(keyString)) {
                FILTERED
              } else {
                filterSensitiveValues(entryValue)
              }
          }
        is List<*> -> value.map(::filterSensitiveValues)
        else -> value
      }
  }
}

/** Resolves the serial name and arguments of a back stack entry. */
internal interface SerialNameLookup {
  /** Returns the serial name of [type], or `null` if it can't be resolved. */
  fun serialNameOrNull(type: Class<*>): String?

  /**
   * Returns the properties of [backStackEntry] as plain maps, lists and scalars, or `null` if they
   * can't be encoded. An entry without properties returns an empty map.
   */
  fun argumentsOrNull(backStackEntry: Any): Map<String, Any?>?
}
