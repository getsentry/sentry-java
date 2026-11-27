package io.sentry.compose.navigation3.internal.backstack.kotlinx

import io.sentry.ILogger
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.contract.UNKNOWN_ENTRY_NAME
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReason
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReasonReporter
import io.sentry.util.ExceptionUtils
import java.lang.reflect.GenericSignatureFormatError
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.MalformedParameterizedTypeException
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
   * May throw, as the lookup can run the app's serializer code for the first time. Must rethrow a
   * fatal error unwrapped if reflection wrapped it in an [InvocationTargetException], as
   * [reflectiveSerializerOrNull] does, since the mapper treats every wrapped error as non-fatal.
   */
  private val lookUpSerializer: (Class<*>) -> KSerializer<*>? = ::reflectiveSerializerOrNull,
  /**
   * Whether a back stack entry's class is annotated with `@Serializable`.
   *
   * May throw, as reading the class's annotations can fail.
   */
  private val isAnnotatedSerializable: (Class<*>) -> Boolean = serializableAnnotationCheck(),
  probe: CompatibilityProbe = { onInconclusive ->
    KotlinxCompatibilityProbe.nameLookupLinkageError(onInconclusive = onInconclusive)
  },
  logger: () -> ILogger,
) : BackStackEntryMapper<Any> {

  // Serializer lookups use reflection and mapping runs on every navigation, so we cache each
  // class's name. Cache size is bounded by the number of back stack entry types.
  private val types = ConcurrentHashMap<Class<*>, CachedType>()

  private val diagnostics = KotlinxMapperDiagnostics(logger)

  init {
    // Throws if the host app uses an incompatible kotlinx-serialization-core version. Causes
    // creation to fail and every back stack entry to be recorded as "/unknown".
    probe { error -> diagnostics.inconclusiveProbe(error) }?.let { throw it }
  }

  override fun map(backStackEntry: Any): SentryBackStackEntry? {
    // An enum constant with a body is an instance of an anonymous subclass, which isn't
    // @Serializable itself, so we look up the enum class instead.
    val type = (backStackEntry as? Enum<*>)?.declaringJavaClass ?: backStackEntry.javaClass
    val cached =
      when (val cachedType = cachedType(type)) {
        is CachedType.Resolved -> cachedType
        is CachedType.Unresolved -> {
          UnknownEntryReasonReporter.report(cachedType.reason)
          return null
        }
      }

    return SentryBackStackEntry(cached.name)
  }

  private fun cachedType(type: Class<*>): CachedType =
    // getOrPut may look up a type more than once if threads race on its first entry. That can cause
    // a failed lookup to be logged more than once, but only one result will ever be cached.
    types.getOrPut(type) { lookUp(type) }

  /**
   * Looks up [type]'s serializer and serial name. Logs if the serializer isn't available or the
   * name can't be resolved.
   *
   * The lookup can run the app's serializer code and fail for the provided `type`. Rather than
   * throw, this method returns [CachedType.Unresolved] with the reason. That allows the caller to
   * cache the returned value, so that failing lookups are queried and logged only once per type.
   */
  @OptIn(ExperimentalSerializationApi::class)
  private fun lookUp(type: Class<*>): CachedType {
    checkEligibleForLookup(type)?.let {
      return CachedType.Unresolved(reason = it)
    }

    return try {
      val serializer = lookUpSerializer(type)
      if (serializer == null) {
        unresolved(type, error = null)
      } else {
        CachedType.Resolved(serializer.descriptor.serialName)
      }
    } catch (e: InvocationTargetException) {
      // The type's serializer() threw, e.g., a custom serializer's ExceptionInInitializerError.
      unresolved(type, e.cause ?: e)
    } catch (e: LinkageError) {
      // Construction checks the library itself, so this likely comes from the type's serializer,
      // e.g., one compiled against another kotlinx.serialization version. It fails only this type.
      unresolved(type, e)
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
      // Broad b/c reflection throws its own unchecked exceptions (e.g., TypeNotPresentException
      // when R8 removed a custom serializer), and custom descriptors may throw anything. The type
      // is cached as unresolved to prevent re-failing on every navigation.
      ExceptionUtils.rethrowIfFatal(t)
      unresolved(type, t)
    }
  }

  /**
   * Checks whether [type] is eligible for serializer retrieval.
   *
   * Returns `null` if eligible; otherwise returns the reason for ineligibility and logs.
   */
  private fun checkEligibleForLookup(type: Class<*>): UnknownEntryReason? =
    checkSerializableAnnotation(type)
      ?: if (isGeneric(type)) {
        diagnostics.genericType(type)
        UnknownEntryReason.GENERIC_ENTRY_TYPE
      } else {
        null
      }

  /**
   * Checks that [type] is annotated with `@Serializable` so that built-in types and unannotated
   * enums aren't named using unhelpful or potentially obfuscated serial names.
   *
   * Returns `null` if the annotation is present; otherwise returns the failure reason and logs.
   */
  private fun checkSerializableAnnotation(type: Class<*>): UnknownEntryReason? =
    try {
      if (isAnnotatedSerializable(type)) {
        null
      } else {
        diagnostics.notSerializable(type)
        UnknownEntryReason.ENTRY_NOT_ANNOTATED
      }
    } catch (e: LinkageError) {
      // Fails only this type.
      unresolved(type, e).reason
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
      // Broad b/c annotation parsing has undocumented failure modes.
      ExceptionUtils.rethrowIfFatal(t)
      unresolved(type, t).reason
    }

  /**
   * Whether [type] is generic (i.e., has type parameters). If so, serialization requires type
   * arguments that can't be recovered from an entry at runtime.
   *
   * Returns `false` if `type`'s generic signature can't be read (e.g., because it's malformed, the
   * type was removed by R8, etc.).
   */
  private fun isGeneric(type: Class<*>): Boolean =
    try {
      type.typeParameters.isNotEmpty()
    } catch (_: GenericSignatureFormatError) {
      false
    } catch (_: MalformedParameterizedTypeException) {
      false
    } catch (_: TypeNotPresentException) {
      false
    }

  /**
   * Returns an unresolved cached type based on the provided [type], and logs.
   *
   * [error] is the exception thrown when trying to read the `type`'s annotations, serializer, or
   * descriptor.
   */
  private fun unresolved(type: Class<*>, error: Throwable?): CachedType.Unresolved {
    diagnostics.unresolved(type, error)
    return CachedType.Unresolved(
      reason =
        if (error == null) {
          UnknownEntryReason.SERIALIZER_NOT_FOUND
        } else {
          UnknownEntryReason.SERIALIZER_ACCESS_THREW
        },
    )
  }

  /** What [lookUp] found for a back stack entry type, cached so each type is looked up once. */
  private sealed interface CachedType {

    /** A type that can be named, and its [name]. */
    class Resolved(val name: String) : CachedType

    /**
     * A type that can't be named, e.g., because it isn't `@Serializable` or reading its serializer
     * failed. Its entries are recorded as [UNKNOWN_ENTRY_NAME], with [reason].
     */
    class Unresolved(val reason: UnknownEntryReason) : CachedType
  }

  private companion object {

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
 * The warnings [KotlinxBackStackEntryMapper] logs if it can't find the serializer for a back stack
 * entry or resolve the entry's name.
 *
 * Only decides what's logged. Whether a type is looked up again is up to the mapper.
 *
 * The R8 check reads each unnamed type's reason from these messages, so rewording one requires
 * updating the fragments in its `UnknownEntryReasonLogs.kt`
 * (`sentry-android-integration-tests/nav3-built-in-back-stack-mappers-r8-check`).
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

  /** Logs that serial names for entries of [type] can't be resolved because the type is generic. */
  fun genericType(type: Class<*>) {
    logger()
      .log(
        WARNING,
        "Nav3 can't name entries of type %s because the type has type parameters. Using " +
          "$UNKNOWN_ENTRY_NAME. Use a custom BackStackEntryMapper.",
        type.name,
      )
  }

  /**
   * Logs that the probe couldn't verify support for name lookups, due to [error] or a missing probe
   * serializer if the error is `null`. The mapper handles subsequent failures per type.
   */
  fun inconclusiveProbe(error: Throwable?) {
    // DEBUG b/c an inconclusive probe doesn't affect what's recorded.
    logger()
      .log(
        DEBUG,
        error,
        "Nav3 couldn't verify kotlinx.serialization support for name lookups because %s. " +
          "Handling failures per entry type.",
        if (error != null) PROBE_THREW else PROBE_SERIALIZER_NOT_FOUND,
      )
  }

  /**
   * Logs why [type] can't be named. [error] is what reading its annotations, serializer, or
   * descriptor threw, or `null` if the serializer lookup found none without throwing.
   *
   * [error] is attached in full. The lookup never sees an entry's values, so it only contains the
   * app's own messages (e.g., from a custom serializer) rather than nav route info.
   */
  fun unresolved(type: Class<*>, error: Throwable?) {
    val reason = if (error != null) LOOK_UP_FAILED else SERIALIZER_NOT_FOUND
    val advice =
      // An ExceptionInInitializerError only means the app's own initializer threw an exception.
      if (error is LinkageError && error !is ExceptionInInitializerError) {
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

    /** Why a type can't be named, if reading its annotations, serializer, or descriptor threw. */
    const val LOOK_UP_FAILED: String = "reading its annotations, serializer, or descriptor failed"

    /** Why the compatibility probe was inconclusive, if it threw. */
    const val PROBE_THREW: String = "the compatibility probe threw (see the attached error)"

    /** Why the compatibility probe was inconclusive, if a probe type's serializer wasn't found. */
    const val PROBE_SERIALIZER_NOT_FOUND: String = "a probe serializer wasn't found"

    /** Why a type can't be named, if its serializer lookup found none without throwing. */
    const val SERIALIZER_NOT_FOUND: String =
      "no serializer was found (the type may be generic or require R8 keep rules)"
  }
}
