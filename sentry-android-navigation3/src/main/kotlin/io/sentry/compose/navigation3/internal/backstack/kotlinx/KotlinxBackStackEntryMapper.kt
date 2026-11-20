package io.sentry.compose.navigation3.internal.backstack.kotlinx

import io.sentry.ILogger
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.NormalizedSentryBackStackEntry.Companion.UNKNOWN_ENTRY_NAME
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.INCOMPATIBLE_KOTLINX_ADVICE
import io.sentry.util.ExceptionUtils
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** Implementation of [BackStackEntryMapper.SerialName]. */
internal class KotlinxBackStackEntryMapper(
  /**
   * Returns the serializer of a back stack entry's `@Serializable` class, or `null` if there's none
   * (e.g. for a generic class). Only called for classes marked `@Serializable`.
   *
   * May throw, as the lookup can run the app's serializer code for the first time.
   */
  private val lookUpSerializer: (Class<*>) -> KSerializer<*>? = ::reflectiveSerializerOrNull,
  logger: () -> ILogger,
  probe: KotlinxCompatibilityProbe = KotlinxCompatibilityProbe,
  /**
   * Whether a back stack entry's class is annotated with `@Serializable`.
   *
   * May throw, as reading the class's annotations can fail.
   */
  private val isAnnotatedSerializable: (Class<*>) -> Boolean = serializableAnnotationCheck(),
) : BackStackEntryMapper<Any> {

  // Serializer lookups use reflection and mapping runs on every navigation, so we cache each
  // class's name. Cache size is bounded by the number of back stack entry types.
  private val types = ConcurrentHashMap<Class<*>, CachedType>()

  private val diagnostics = KotlinxMapperDiagnostics(logger)

  init {
    // Check name-lookup support during construction so an incompatible kotlinx.serialization
    // version triggers the use of UnavailableBackStackEntryMapper. Later LinkageErrors likely come
    // from the entry's own type and are handled per type.
    probe
      .nameLookupLinkageError { error ->
        diagnostics.inconclusiveProbe(NAME_LOOKUP_CAPABILITY, error)
      }
      ?.let { throw it }
  }

  override fun map(backStackEntry: Any): SentryBackStackEntry? {
    // An enum constant with a body is an instance of an anonymous subclass, which isn't
    // @Serializable itself, so we look up the enum class instead.
    val type = (backStackEntry as? Enum<*>)?.declaringJavaClass ?: backStackEntry.javaClass
    val cached = cachedType(type) as? CachedType.Resolved ?: return null

    return SentryBackStackEntry(cached.name)
  }

  private fun cachedType(type: Class<*>): CachedType =
    // getOrPut may look up a type more than once if threads race on its first entry. That can cause
    // a failed lookup to be logged more than once, but only one result will ever be cached.
    types.getOrPut(type) { lookUp(type) }

  /**
   * Looks up [type]'s serializer and name. Logs if it can't be named.
   *
   * Never throws for a non-fatal error. Instead, a lookup that fails returns
   * [CachedType.Unresolved], which [cachedType] caches, so each type's failure is queried and
   * logged only once.
   */
  @OptIn(ExperimentalSerializationApi::class)
  private fun lookUp(type: Class<*>): CachedType {
    // Checked before the lookup, which would otherwise return serializers for types that aren't
    // @Serializable.
    if (!isMarkedSerializable(type)) {
      return CachedType.Unresolved
    }

    // Serializer lookup and descriptor access may run host app code. Failures affect only this
    // type.
    val serializer =
      when (val lookup = lookUpSerializerCatching(type)) {
        is SerializerLookup.Found -> lookup.serializer
        is SerializerLookup.NotFound -> {
          diagnostics.unresolved(type, lookup.error)
          return CachedType.Unresolved
        }
      }

    return try {
      CachedType.Resolved(serializer.descriptor.serialName)
    } catch (e: LinkageError) {
      // Treat descriptor linkage errors as type-specific, including incompatibilities caused by a
      // serializer compiled against another kotlinx.serialization version.
      diagnostics.unresolved(type, e)
      CachedType.Unresolved
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
      // Custom descriptors may throw anything. Catch broadly to log once and cache the type as
      // unresolved, avoiding repeated lookups. Entries use UNKNOWN_ENTRY_NAME.
      ExceptionUtils.rethrowIfFatal(t)
      diagnostics.unresolved(type, t)
      CachedType.Unresolved
    }
  }

  /**
   * Whether [type] is annotated with `@Serializable`. Logs why it can't be named if the annotation
   * is absent or can't be read.
   *
   * We only name types the host app marks as `@Serializable`. kotlinx.serialization provides
   * serializers for built-in types (e.g. String, Int) and enums, but their serial names would be
   * misleading ("kotlin.String") or derived from an obfuscated class name.
   */
  private fun isMarkedSerializable(type: Class<*>): Boolean =
    try {
      isAnnotatedSerializable(type).also { isMarked ->
        if (!isMarked) {
          diagnostics.notSerializable(type)
        }
      }
    } catch (e: LinkageError) {
      // As for the serializer lookup, a LinkageError from reading this type's annotations only
      // fails this type.
      diagnostics.unresolved(type, e)
      false
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
      // Catch broadly because annotation parsing has undocumented failure modes. Cache failures as
      // unresolved so the type records UNKNOWN_ENTRY_NAME and logs once, rather than throwing from
      // map() and retrying on every navigation.
      ExceptionUtils.rethrowIfFatal(t)
      diagnostics.unresolved(type, t)
      false
    }

  /**
   * Looks up [type]'s serializer, returning the error that made it fail, if any, instead of
   * throwing it.
   */
  private fun lookUpSerializerCatching(type: Class<*>): SerializerLookup =
    try {
      lookUpSerializer(type)?.let(SerializerLookup::Found)
        ?: SerializerLookup.NotFound(error = null)
    } catch (e: InvocationTargetException) {
      // kotlinx.serialization (or our fallback for private objects) calls the type's
      // generated serializer() method reflectively, and it threw. For instance, a custom
      // serializer object whose initializer throws arrives here with an
      // ExceptionInInitializerError as its cause. Only this type is affected.
      val cause = e.cause ?: e
      // Reflection wraps everything the method throws, fatal errors included, so we apply the
      // SDK's fatal-error check to the cause. A LinkageError cause (e.g., that
      // ExceptionInInitializerError) only fails this type, so it's exempt.
      if (cause !is LinkageError) {
        ExceptionUtils.rethrowIfFatal(cause)
      }
      SerializerLookup.NotFound(cause)
    } catch (e: ReflectiveOperationException) {
      // Any other reflective failure is specific to this type's serializer members.
      SerializerLookup.NotFound(e)
    } catch (e: LinkageError) {
      // The creation-time probe checks library compatibility. A later LinkageError may come
      // from this type's serializer or its dependencies, so cache it as unresolved without
      // disabling other types. Catch before rethrowIfFatal, which rethrows LinkageErrors.
      SerializerLookup.NotFound(e)
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
      // Broad because the lookup reflects over the app's classes, and the reflection and
      // annotation APIs it uses can throw unchecked exceptions of their own (e.g., a
      // TypeNotPresentException when R8 removed the serializer a @Serializable(with = ...)
      // names). Without this catch, the exception would escape map() uncached and the lookup
      // would be repeated on every navigation. Instead, the type is cached as unresolved and
      // recorded as UNKNOWN_ENTRY_NAME, with the error logged once.
      ExceptionUtils.rethrowIfFatal(t)
      SerializerLookup.NotFound(t)
    }

  /** What [lookUp] found for a back stack entry type, cached so each type is looked up once. */
  private sealed interface CachedType {

    /** A type that can be named, and its [name]. */
    class Resolved(val name: String) : CachedType

    /**
     * A type that can't be named, e.g., because it isn't `@Serializable` or reading its serializer
     * failed. Its entries are recorded as [UNKNOWN_ENTRY_NAME].
     */
    object Unresolved : CachedType
  }

  /** The result of looking up a type's serializer. */
  private sealed interface SerializerLookup {

    class Found(val serializer: KSerializer<*>) : SerializerLookup

    /**
     * No serializer was found. [error] is what made the lookup fail, or `null` if the lookup found
     * none without throwing (e.g., for a generic class).
     */
    class NotFound(val error: Throwable?) : SerializerLookup
  }

  internal companion object {
    /** The capability the name lookup check verifies, as its inconclusive log names it. */
    const val NAME_LOOKUP_CAPABILITY: String = "name lookups"

    /** Returns whether a class is annotated with `@Serializable`. */
    private fun serializableAnnotationCheck(): (Class<*>) -> Boolean {
      // Resolved here, when the mapper is created, rather than on the first map() call, so that if
      // the annotation class can't be linked, creating this mapper fails and
      // BuiltInBackStackEntryMappers falls back to UnavailableBackStackEntryMapper.
      val serializableAnnotation: Class<Serializable> = Serializable::class.java
      return { type -> type.isAnnotationPresent(serializableAnnotation) }
    }
  }
}

/**
 * The warnings [KotlinxBackStackEntryMapper] logs when it can't name a back stack entry.
 *
 * Only decides what's logged. Whether a type is looked up again is up to the mapper.
 */
internal class KotlinxMapperDiagnostics(private val logger: () -> ILogger) {

  /** Logs that entries of [type] can't be named because the type isn't `@Serializable`. */
  fun notSerializable(type: Class<*>) {
    logger()
      .log(
        WARNING,
        "Nav3 can't name entries of type %s because the type is not @Serializable. Using " +
          "$UNKNOWN_ENTRY_NAME. Add @Serializable or use a custom BackStackEntryMapper.",
        type.name,
      )
  }

  /**
   * Logs that the probe couldn't verify support for [capability], due to [error] or a missing probe
   * serializer if the error is `null`. The mapper handles subsequent failures per type.
   */
  fun inconclusiveProbe(capability: String, error: Throwable?) {
    // DEBUG b/c an inconclusive probe doesn't affect what's recorded.
    logger()
      .log(
        DEBUG,
        error,
        "Nav3 couldn't verify kotlinx.serialization support for %s because %s. Handling " +
          "failures per entry type.",
        capability,
        if (error != null) PROBE_THREW else PROBE_SERIALIZER_NOT_FOUND,
      )
  }

  /**
   * Logs why [type] can't be named. [error] is what reading its annotations, serializer, or
   * descriptor threw, or `null` if the serializer lookup found none without throwing.
   *
   * [error] is attached as is. Looking up a type's serializer never sees an entry's values, so its
   * errors can't contain route data, although their messages can be the app's own text (e.g., from
   * a custom serializer or a static initializer). Like the exceptions the app's `beforeSend`
   * callback throws, they're logged in full, which only happens with `debug` enabled.
   */
  fun unresolved(type: Class<*>, error: Throwable?) {
    val reason = if (error != null) LOOK_UP_FAILED else SERIALIZER_NOT_FOUND
    val advice =
      if (error is LinkageError) {
        // The probe can miss an incompatible version, which then fails every type one at a time.
        "See the attached error. If all types fail, kotlinx.serialization may be incompatible. " +
          INCOMPATIBLE_KOTLINX_ADVICE
      } else if (error != null) {
        "See the attached error or use a custom BackStackEntryMapper."
      } else {
        "See BackStackEntryMapper.SerialName's documentation or use a custom BackStackEntryMapper."
      }
    logger()
      .log(
        WARNING,
        error,
        "Nav3 can't name entries of type %s because %s. Using $UNKNOWN_ENTRY_NAME. $advice",
        type.name,
        reason,
      )
  }

  companion object {

    /** Why the compatibility probe was inconclusive, if it threw. */
    const val PROBE_THREW: String = "the compatibility probe threw (see the attached error)"

    /** Why the compatibility probe was inconclusive, if a probe type's serializer wasn't found. */
    const val PROBE_SERIALIZER_NOT_FOUND: String = "a probe serializer wasn't found"

    /** Why a type can't be named, if reading its annotations, serializer, or descriptor threw. */
    const val LOOK_UP_FAILED: String = "reading its annotations, serializer, or descriptor failed"

    /** Why a type can't be named, if its serializer lookup found none without throwing. */
    const val SERIALIZER_NOT_FOUND: String =
      "no serializer was found (the type may be generic or require R8 keep rules)"

    /**
     * The oldest kotlinx.serialization version [KotlinxBackStackEntryMapper] supports.
     *
     * Must match `kotlinxSerialization-floor` in `gradle/dependency-matrix.versions.toml`.
     */
    const val MIN_KOTLINX_SERIALIZATION_VERSION: String = "1.7.3"

    const val INCOMPATIBLE_KOTLINX_ADVICE: String =
      "Requires kotlinx.serialization $MIN_KOTLINX_SERIALIZATION_VERSION or later. " +
        "Upgrade kotlinx.serialization or the Sentry SDK. If both are current, report this at " +
        "https://github.com/getsentry/sentry-java/issues/new/choose. Use a custom " +
        "BackStackEntryMapper as a workaround."
  }
}
