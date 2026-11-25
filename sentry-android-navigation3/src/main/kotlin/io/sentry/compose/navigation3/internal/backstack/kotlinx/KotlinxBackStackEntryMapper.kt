package io.sentry.compose.navigation3.internal.backstack.kotlinx

import io.sentry.ILogger
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_DEPTH
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_VALUES
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.compose.navigation3.internal.backstack.NormalizedSentryBackStackEntry.Companion.UNKNOWN_ENTRY_NAME
import io.sentry.compose.navigation3.internal.backstack.UnnamedReason
import io.sentry.compose.navigation3.internal.backstack.UnnamedReasonReports
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.INCOMPATIBLE_KOTLINX_ADVICE
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING
import io.sentry.util.ExceptionUtils
import java.lang.reflect.GenericSignatureFormatError
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.MalformedParameterizedTypeException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind

/**
 * Implementation of [BackStackEntryMapper.SerialName] and
 * [BackStackEntryMapper.SerialNameAndProperties].
 */
internal class KotlinxBackStackEntryMapper(
  /**
   * True if this mapper should return each back stack entry's properties as arguments; false if
   * they should be omitted.
   */
  private val includeProperties: Boolean,
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
  // class's serializer and name. Cache size is bounded by the number of back stack entry types.
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

  /**
   * Whether the compatibility probe disabled property encoding. Entries with properties record
   * [ArgumentDropReason.KOTLINX_SERIALIZATION_VERSION], which is sent with them, so the problem is
   * visible without debug logs; names are unaffected.
   */
  private val isEncodingIncompatible: Boolean =
    includeProperties &&
      run {
        val error = probe.encoderIncompatibility() ?: return@run false
        diagnostics.incompatibleEncoder(error)
        true
      }

  override fun map(backStackEntry: Any): SentryBackStackEntry? {
    // An enum constant with a body is an instance of an anonymous subclass, which isn't
    // @Serializable itself, so we look up the enum class instead.
    val type = (backStackEntry as? Enum<*>)?.declaringJavaClass ?: backStackEntry.javaClass
    val cached =
      when (val cachedType = cachedType(type)) {
        is CachedType.Resolved -> cachedType
        is CachedType.Unresolved -> {
          UnnamedReasonReports.report(cachedType.reason)
          return null
        }
      }

    val arguments = if (includeProperties) argumentsOf(type, cached, backStackEntry) else null

    return SentryBackStackEntry(cached.name, arguments)
  }

  private fun cachedType(type: Class<*>): CachedType =
    // getOrPut may look up a type more than once if threads race on its first entry. That can cause
    // a failed lookup to be logged more than once, but only one result will ever be cached.
    types.getOrPut(type) { lookUp(type) }

  /**
   * Looks up [type]'s serializer and name. Logs if it can't be named.
   *
   * Never throws for a non-fatal error. Instead, a lookup that fails returns
   * [CachedType.Unresolved] with the reason, which [cachedType] caches, so each type's failure is
   * queried and logged only once.
   */
  @OptIn(ExperimentalSerializationApi::class)
  private fun lookUp(type: Class<*>): CachedType {
    preLookupProblem(type)?.let {
      return CachedType.Unresolved(it)
    }

    // Serializer lookup and descriptor access may run host app code. Failures affect only this
    // type.
    val serializer =
      when (val lookup = lookUpSerializerCatching(type)) {
        is SerializerLookup.Found -> lookup.serializer
        is SerializerLookup.NotFound -> {
          diagnostics.unresolved(type, lookup.error)
          return CachedType.Unresolved(
            if (lookup.error == null) {
              UnnamedReason.SERIALIZER_NOT_FOUND
            } else {
              UnnamedReason.SERIALIZER_LOOKUP_THREW
            },
          )
        }
      }

    return try {
      val descriptor = serializer.descriptor
      // SerialName only needs the name. Read it before inspecting the shape so it isn't lost if
      // shape lookup fails.
      val name = descriptor.serialName
      CachedType.Resolved(serializer, name, includeProperties && hasProperties(descriptor))
    } catch (e: LinkageError) {
      // Treat descriptor linkage errors as type-specific, including incompatibilities caused by a
      // serializer compiled against another kotlinx.serialization version.
      diagnostics.unresolved(type, e)
      CachedType.Unresolved(UnnamedReason.SERIALIZER_LOOKUP_THREW)
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
      // Custom descriptors may throw anything. Catch broadly to log once and cache the type as
      // unresolved, avoiding repeated lookups. Entries use UNKNOWN_ENTRY_NAME.
      ExceptionUtils.rethrowIfFatal(t)
      diagnostics.unresolved(type, t)
      CachedType.Unresolved(UnnamedReason.SERIALIZER_LOOKUP_THREW)
    }
  }

  /**
   * Returns why [type] can't be named before looking up its serializer, logging it, or `null` if
   * the lookup should decide.
   *
   * The annotation is checked first, as the lookup would otherwise return serializers for types
   * that aren't `@Serializable`. Generic types are checked next: the lookup can't find their
   * serializers either, and this way the reason and the warning say why.
   */
  private fun preLookupProblem(type: Class<*>): UnnamedReason? =
    serializableAnnotationProblem(type)
      ?: if (isGeneric(type)) {
        diagnostics.genericType(type)
        UnnamedReason.GENERIC_TYPE
      } else {
        null
      }

  /**
   * Returns why [type] can't be named if it isn't annotated with `@Serializable` or its annotations
   * can't be read, logging it, or `null` if it's annotated.
   *
   * We only name types the host app marks as `@Serializable`. kotlinx.serialization provides
   * serializers for built-in types (e.g. String, Int) and enums, but their serial names would be
   * misleading ("kotlin.String") or derived from an obfuscated class name.
   */
  private fun serializableAnnotationProblem(type: Class<*>): UnnamedReason? =
    try {
      if (isAnnotatedSerializable(type)) {
        null
      } else {
        diagnostics.notSerializable(type)
        UnnamedReason.NOT_SERIALIZABLE
      }
    } catch (e: LinkageError) {
      // As for the serializer lookup, a LinkageError from reading this type's annotations only
      // fails this type.
      diagnostics.unresolved(type, e)
      UnnamedReason.SERIALIZER_LOOKUP_THREW
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
      // Catch broadly because annotation parsing has undocumented failure modes. Cache failures as
      // unresolved so the type records UNKNOWN_ENTRY_NAME and logs once, rather than throwing from
      // map() and retrying on every navigation.
      ExceptionUtils.rethrowIfFatal(t)
      diagnostics.unresolved(type, t)
      UnnamedReason.SERIALIZER_LOOKUP_THREW
    }

  /**
   * Whether [type] has type parameters, so its serializer needs type arguments that can't be
   * recovered from an entry at runtime.
   *
   * `false` if its generic signature can't be read, e.g., because it's malformed, so that the
   * lookup decides. R8 may also remove generic signatures, which also leaves the lookup to decide,
   * as it can't find a generic class's serializer either.
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

  /**
   * Whether entries described by [descriptor] may have properties. A class or object without
   * properties records no arguments, so its entries skip encoding.
   */
  @OptIn(ExperimentalSerializationApi::class)
  private fun hasProperties(descriptor: SerialDescriptor): Boolean =
    try {
      descriptor.elementsCount > 0 ||
        (descriptor.kind != StructureKind.CLASS && descriptor.kind != StructureKind.OBJECT)
    } catch (_: LinkageError) {
      // Preserve the resolved name and attempt encoding, which handles descriptor failures.
      true
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
      // Broad because a custom serializer's descriptor is host app code, which may throw anything.
      // The entry keeps its name, and encoding, which reads the same descriptor, records why its
      // arguments are dropped.
      ExceptionUtils.rethrowIfFatal(t)
      true
    }

  /**
   * Returns lazily encoded arguments derived from [backStackEntry]'s properties, or `null` if its
   * [type] has no properties.
   */
  private fun argumentsOf(
    type: Class<*>,
    cached: CachedType.Resolved,
    backStackEntry: Any,
  ): Map<String, Any?>? {
    if (!cached.hasProperties) {
      return null
    }
    if (isEncodingIncompatible) {
      return KotlinxUnencodableArguments
    }
    @Suppress("UNCHECKED_CAST")
    return KotlinxArgumentSource(
      backStackEntry,
      cached.serializer as KSerializer<Any>,
      onUnencodableValue = { diagnostics.unencodableValue(type) },
    ) { error ->
      diagnostics.propertyFailure(type, error)
    }
  }

  /** What [lookUp] found for a back stack entry type, cached so each type is looked up once. */
  private sealed interface CachedType {

    /**
     * A type that can be named: its [serializer], its [name], and whether entries of the type may
     * have properties to encode.
     */
    class Resolved(
      val serializer: KSerializer<*>,
      val name: String,
      val hasProperties: Boolean,
    ) : CachedType

    /**
     * A type that can't be named, e.g., because it isn't `@Serializable` or reading its serializer
     * failed. Its entries are recorded as [UNKNOWN_ENTRY_NAME], with [reason].
     */
    class Unresolved(val reason: UnnamedReason) : CachedType
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
 * The warnings [KotlinxBackStackEntryMapper] logs when it can't name a back stack entry or encode
 * its properties.
 *
 * Only decides what's logged, and how often. Whether a type is looked up or encoded again is up to
 * the mapper.
 */
internal class KotlinxMapperDiagnostics(private val logger: () -> ILogger) {

  /**
   * The kinds of property encoding failure already logged for each type. Failing types are still
   * encoded again, as whether encoding fails can depend on the value, so this only keeps each
   * warning from being logged more than once per type.
   */
  private val loggedPropertyFailures =
    Collections.newSetFromMap(ConcurrentHashMap<LoggedPropertyFailure, Boolean>())

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
   * Logs that entries of [type] can't be named because the type is generic, so its serializer needs
   * type arguments that can't be recovered from an entry.
   */
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
   * Logs [error] indicating that the app's kotlinx.serialization version is incompatible with the
   * properties encoder. Entries are recorded by name without arguments.
   */
  fun incompatibleEncoder(error: Throwable) {
    logger()
      .log(
        WARNING,
        "Nav3 SerialNameAndProperties is incompatible with this app's kotlinx.serialization " +
          "version. Recording names without arguments. $INCOMPATIBLE_KOTLINX_ADVICE",
        error,
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

  /**
   * Logs why encoding the properties of an entry of [type] failed. Logged once per type and kind of
   * failure, as whether encoding fails can depend on the entry's values (e.g., whether a contextual
   * property is null, or which subtype a sealed property has). A broken argument limit is logged
   * once per type and limit, as each limit's warning describes a different fix.
   *
   * Serializer exceptions can contain route data in their messages or causes, so for those we log
   * only the entry type and the failure category. A [LinkageError] is attached as
   * [linkageDiagnostic] makes it, as its symbol helps diagnose an incompatible version. Unlike for
   * [unresolved], the error is thrown while the entry's serializers run, so an app's serializer may
   * have thrown it or a class initializer may have put app data in its cause.
   */
  fun propertyFailure(type: Class<*>, error: Throwable) {
    when (error) {
      is DropArguments ->
        logOncePerType(type, PropertyFailure.OVER_LIMITS, error.reason) {
          log(
            WARNING,
            "Nav3 dropped arguments for type %s (%s): %s. Use a custom BackStackEntryMapper to " +
              "select fewer properties or flatten nested values.",
            type.name,
            error.reason.serializedValue,
            brokenLimit(error.reason),
          )
        }
      is SerializationException ->
        logOncePerType(type, PropertyFailure.UNENCODABLE) {
          log(
            WARNING,
            "Nav3 couldn't encode properties for type %s. Recording names without arguments. " +
              "Entries that are themselves contextual or open polymorphic, unencodable map keys, " +
              "and values whose serializers fail partway aren't supported. Use a custom " +
              "BackStackEntryMapper to select supported properties or extract arguments directly.",
            type.name,
          )
        }
      is LinkageError ->
        logOncePerType(type, PropertyFailure.UNLOADABLE) {
          log(
            WARNING,
            linkageDiagnostic(error),
            "Nav3 couldn't encode properties for type %s because a required class couldn't be " +
              "linked, initialized, or found. Recording names without arguments. See the " +
              "attached error. If all types fail, kotlinx.serialization may be incompatible. " +
              INCOMPATIBLE_KOTLINX_ADVICE,
            type.name,
          )
        }
      else ->
        logOncePerType(type, PropertyFailure.THROWING) {
          log(
            WARNING,
            "Nav3 couldn't encode properties for type %s because a serializer threw. Recording " +
              "names without arguments.",
            type.name,
          )
        }
    }
  }

  /**
   * Logs that some values of an entry of [type] were recorded as
   * [KotlinxPropertiesEncoder.UNENCODABLE_SUBSTITUTE] because their serializers couldn't encode
   * them. Logged once per type, like [propertyFailure], and for the same reason: which values fail
   * can depend on the entry. The serializer's exception isn't logged, as its message can contain
   * route data.
   */
  fun unencodableValue(type: Class<*>) {
    logOncePerType(type, PropertyFailure.SUBSTITUTED) {
      log(
        WARNING,
        "Nav3 couldn't encode some property values for type %s. Recording them as " +
          "${KotlinxPropertiesEncoder.UNENCODABLE_SUBSTITUTE}. Contextual values, " +
          "open polymorphic values (e.g., non-sealed interfaces or Parcelable properties), " +
          "and maps whose keys are classes, collections, or polymorphic values " +
          "aren't supported. Use a custom BackStackEntryMapper to extract those " +
          "values directly.",
        type.name,
      )
    }
  }

  /**
   * Returns what [propertyFailure] attaches for [error], a [LinkageError] thrown while an entry's
   * serializers ran.
   *
   * An error the JVM raises when it can't link a class or member names only that symbol in its
   * message, so one without a cause is attached as is. Otherwise, the attached error keeps the
   * class name and stack trace, which name only code, and the message only if the JVM raises such
   * errors. It drops the cause, e.g., the [ExceptionInInitializerError] behind a
   * [NoClassDefFoundError], whose message is the app's own text.
   */
  private fun linkageDiagnostic(error: LinkageError): Throwable {
    val namesSymbol = error is IncompatibleClassChangeError || error is NoClassDefFoundError
    if (namesSymbol && error.cause == null) {
      return error
    }
    return RedactedLinkageError(error, keepMessage = namesSymbol)
  }

  /** A [LinkageError]'s class name, stack trace, and optionally its message, without its cause. */
  private class RedactedLinkageError(error: LinkageError, keepMessage: Boolean) :
    RuntimeException(
      listOfNotNull(error.javaClass.name, error.message?.takeIf { keepMessage }).joinToString(": "),
    ) {
    init {
      stackTrace = error.stackTrace
    }
  }

  /**
   * Describes the limit that encoding an entry's properties broke, for [propertyFailure].
   *
   * The `when` has no `else`, so adding an [ArgumentDropReason] fails compilation until its
   * description is added here, and the warning can't go on describing limits that no longer exist
   * or leave out new ones. The limits' values come from the constants that enforce them.
   */
  private fun brokenLimit(reason: ArgumentDropReason): String =
    when (reason) {
      ArgumentDropReason.CHARACTER_LIMIT ->
        "its keys and text exceed $MAX_ARGUMENT_CHARACTERS characters"
      ArgumentDropReason.MAX_COUNT -> "it has more than $MAX_ARGUMENT_VALUES values"
      ArgumentDropReason.DUPLICATE_KEY -> "a map has two keys that are recorded as the same text"
      ArgumentDropReason.INVALID_STRUCTURE ->
        "it nests values more than $MAX_ARGUMENT_DEPTH levels deep, or serializers or value " +
          "classes more than $MAX_SERIALIZER_NESTING deep"
      // Not limits, and not reported through this path: encoding failures are logged by their
      // own kind, and the others come from the compatibility probe and sanitization.
      ArgumentDropReason.ENCODING_FAILED,
      ArgumentDropReason.KOTLINX_SERIALIZATION_VERSION,
      ArgumentDropReason.SANITIZATION_FAILED -> "it broke an argument limit"
    }

  /**
   * Runs [log] on the logger unless a [failure] of this kind, and with this [reason] if it has one,
   * was already logged for [type].
   */
  private inline fun logOncePerType(
    type: Class<*>,
    failure: PropertyFailure,
    reason: ArgumentDropReason? = null,
    log: ILogger.() -> Unit,
  ) {
    if (loggedPropertyFailures.add(LoggedPropertyFailure(type, failure, reason))) {
      logger().log()
    }
  }

  /** A property encoding warning that was logged, as [logOncePerType] identifies it. */
  private data class LoggedPropertyFailure(
    val type: Class<*>,
    val failure: PropertyFailure,
    val reason: ArgumentDropReason?,
  )

  /** Why encoding an entry's properties failed, which determines the warning we log. */
  private enum class PropertyFailure {
    /** Encoding threw a [SerializationException], e.g., for a contextual entry. */
    UNENCODABLE,

    /**
     * Some values couldn't be encoded and were recorded as
     * [KotlinxPropertiesEncoder.UNENCODABLE_SUBSTITUTE], e.g., a contextual property.
     */
    SUBSTITUTED,

    /**
     * The entry broke an argument limit, e.g., by having too many values, or has a map with two
     * keys recorded as the same string.
     */
    OVER_LIMITS,

    /** A serializer threw something other than a [SerializationException] or [LinkageError]. */
    THROWING,

    /** A class a serializer needs couldn't be linked, initialized, or found. */
    UNLOADABLE,
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
