package io.sentry.compose.navigation3

import io.sentry.ILogger
import io.sentry.ScopesAdapter
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.NormalizedSentryBackStackEntry.Companion.UNKNOWN_ENTRY_NAME
import io.sentry.util.LoadClass
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of [BackStackEntryMapper.serialName].
 *
 * kotlinx.serialization is a `compileOnly` dependency. Only [KotlinxSerialNameLookup] references
 * it, and this class only touches the lookup after confirming the library is present. That way,
 * apps without kotlinx.serialization never load a class that references it.
 *
 * Resolved names, including failed lookups, are cached per class. Route types are bounded, so the
 * cache is too.
 *
 * @param lookup resolves serial names, or `null` if kotlinx.serialization is unavailable.
 * @param logger supplies the logger at the time a warning is logged.
 */
internal class SerialNameBackStackEntryMapper(
  private val lookup: SerialNameLookup?,
  private val logger: () -> ILogger,
) : BackStackEntryMapper<Any> {

  private val cache = ConcurrentHashMap<Class<*>, CachedSerialName>()

  override fun map(backStackEntry: Any): SentryBackStackEntry? {
    val name = serialNameOf(backStackEntry.javaClass) ?: return null
    return SentryBackStackEntry(name = name)
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

  /** Wraps a nullable name, since [ConcurrentHashMap] doesn't accept `null` values. */
  private class CachedSerialName(val name: String?)

  companion object {
    private const val KSERIALIZER_CLASS_NAME = "kotlinx.serialization.KSerializer"

    private val defaultLogger: () -> ILogger = { ScopesAdapter.getInstance().options.logger }

    val instance: SerialNameBackStackEntryMapper by lazy {
      val lookup =
        if (LoadClass().isClassAvailable(KSERIALIZER_CLASS_NAME, defaultLogger())) {
          KotlinxSerialNameLookup
        } else {
          null
        }
      SerialNameBackStackEntryMapper(lookup, defaultLogger)
    }
  }
}

/** Resolves the serial name of a back stack entry's type. */
internal interface SerialNameLookup {
  /** Returns the serial name of [type], or `null` if it can't be resolved. */
  fun serialNameOrNull(type: Class<*>): String?
}
