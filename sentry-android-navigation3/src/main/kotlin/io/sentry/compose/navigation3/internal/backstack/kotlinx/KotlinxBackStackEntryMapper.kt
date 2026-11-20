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
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.INCOMPATIBLE_KOTLINX_ADVICE
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING
import io.sentry.util.ExceptionUtils
import java.lang.reflect.InvocationTargetException
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
) : BackStackEntryMapper<Any> {

  // Resolved here, rather than on the first map() call, so that if kotlinx.serialization is
  // missing, creating this mapper fails and BuiltInBackStackEntryMappers falls back to
  // UnavailableBackStackEntryMapper.
  private val serializableAnnotation: Class<Serializable> = Serializable::class.java

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
   * `true` if the app's kotlinx.serialization version doesn't support the properties encoder, in
   * which case no arguments are produced for any entries. Instead, entries with properties record
   * [ArgumentDropReason.ENCODING_FAILED] without being encoded. Names are unaffected.
   */
  private val isEncodingIncompatible: Boolean =
    includeProperties &&
      run {
        val error =
          probe.encodingLinkageError { error ->
            diagnostics.inconclusiveProbe(ENCODING_CAPABILITY, error)
          } ?: return@run false
        diagnostics.incompatibleEncoder(error)
        true
      }

  override fun map(backStackEntry: Any): SentryBackStackEntry? {
    // An enum constant with a body is an instance of an anonymous subclass, which isn't
    // @Serializable itself, so we look up the enum class instead.
    val type = (backStackEntry as? Enum<*>)?.declaringJavaClass ?: backStackEntry.javaClass
    val cached = cachedType(type)
    val name = cached.name ?: return null

    val arguments = if (includeProperties) argumentsOf(type, cached, backStackEntry) else null

    return SentryBackStackEntry(name, arguments)
  }

  private fun cachedType(type: Class<*>): CachedType =
    // getOrPut may look up a type more than once if threads race on its first entry. That can cause
    // a failed lookup to be logged more than once, but only one result will ever be cached.
    types.getOrPut(type) { lookUp(type) }

  /**
   * Looks up [type]'s serializer and name. Logs if it can't be named.
   *
   * Never throws for a non-fatal error. Instead, a lookup that fails returns [UNRESOLVED], which
   * [cachedType] caches, so each type's failure is queried and logged only once.
   */
  @OptIn(ExperimentalSerializationApi::class)
  private fun lookUp(type: Class<*>): CachedType {
    // Checked before the lookup, which would otherwise return serializers for types that aren't
    // @Serializable.
    if (!isMarkedSerializable(type)) {
      return UNRESOLVED
    }

    // Serializer lookup and descriptor access may run host app code. Failures affect only this
    // type.
    val lookup = lookUpSerializerCatching(type)
    val serializer = lookup.serializer
    var error = lookup.error

    var hasProperties = false
    val name =
      try {
        serializer?.descriptor?.let { descriptor ->
          // SerialName only needs the name. Read it before inspecting the shape so it isn't lost if
          // shape lookup fails.
          val serialName = descriptor.serialName
          hasProperties = includeProperties && hasProperties(descriptor)
          serialName
        }
      } catch (e: LinkageError) {
        // Treat descriptor linkage errors as type-specific, including incompatibilities caused by a
        // serializer compiled against another kotlinx.serialization version.
        error = e
        null
      } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
        // Custom descriptors may throw anything. Catch broadly to log once and cache the type as
        // unresolved, avoiding repeated lookups. Entries use UNKNOWN_ENTRY_NAME.
        ExceptionUtils.rethrowIfFatal(t)
        error = t
        null
      }

    if (name == null) {
      diagnostics.unresolved(type, error)
    }
    return if (name == null) UNRESOLVED else CachedType(serializer, name, hasProperties)
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
      type.isAnnotationPresent(serializableAnnotation).also { isMarked ->
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
      SerializerLookup(lookUpSerializer(type), error = null)
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
      SerializerLookup(serializer = null, error = cause)
    } catch (e: ReflectiveOperationException) {
      // Any other reflective failure is specific to this type's serializer members.
      SerializerLookup(serializer = null, error = e)
    } catch (e: LinkageError) {
      // A LinkageError, which the SDK otherwise rethrows as fatal, only fails this type. If the
      // app's kotlinx.serialization version couldn't support name lookups at all, the probe that
      // ran when this mapper was created would most likely have failed. So the error most likely
      // comes from linking or initializing a class this type's serializer needs (e.g., a
      // NoSuchMethodError from a serializer compiled against another version, or a
      // NoClassDefFoundError for a class that failed to initialize earlier).
      SerializerLookup(serializer = null, error = e)
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
      // Broad because the lookup reflects over the app's classes, and the reflection and
      // annotation APIs it uses can throw unchecked exceptions of their own (e.g., a
      // TypeNotPresentException when R8 removed the serializer a @Serializable(with = ...)
      // names). Without this catch, the exception would escape map() uncached and the lookup
      // would be repeated on every navigation. Instead, the type is cached as unresolved and
      // recorded as UNKNOWN_ENTRY_NAME, with the error logged once.
      ExceptionUtils.rethrowIfFatal(t)
      SerializerLookup(serializer = null, error = t)
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
      // As for the name, a LinkageError from the type's own descriptor only fails this type.
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
    cached: CachedType,
    backStackEntry: Any,
  ): Map<String, Any?>? {
    if (!cached.hasProperties) {
      return null
    }
    if (isEncodingIncompatible) {
      return KotlinxUnencodableArguments
    }
    @Suppress("UNCHECKED_CAST")
    return KotlinxArguments(backStackEntry, cached.serializer as KSerializer<Any>) { error ->
      diagnostics.propertyFailure(type, error)
    }
  }

  /**
   * A type's serializer and name, or `null`s if the lookup failed, and whether the type has
   * properties to encode. A resolved name implies a serializer.
   */
  private class CachedType(
    val serializer: KSerializer<*>?,
    val name: String?,
    val hasProperties: Boolean = false,
  )

  /** A type's serializer, or the error that made looking it up fail, if any. */
  private class SerializerLookup(val serializer: KSerializer<*>?, val error: Throwable?)

  private companion object {
    private const val ENCODING_CAPABILITY = "property encoding"
    private const val NAME_LOOKUP_CAPABILITY = "name lookups"
    private val UNRESOLVED = CachedType(serializer = null, name = null)
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
    Collections.newSetFromMap(ConcurrentHashMap<Pair<Class<*>, PropertyFailure>, Boolean>())

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
        if (error != null) {
          "the compatibility probe threw (see the attached error)"
        } else {
          "a probe serializer wasn't found"
        },
      )
  }

  /**
   * Logs [error] indicating that the app's kotlinx.serialization version is incompatible with the
   * properties encoder. Entries are recorded by name without arguments.
   */
  fun incompatibleEncoder(error: LinkageError) {
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
   */
  fun unresolved(type: Class<*>, error: Throwable?) {
    val reason =
      if (error != null) {
        "reading its annotations, serializer, or descriptor failed"
      } else {
        "no serializer was found (the type may be generic or require R8 keep rules)"
      }
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
   * property is null, or which subtype a sealed property has).
   *
   * Serializer exceptions can contain route data in their messages or causes, so for those we log
   * only the entry type and the failure category. A [LinkageError] is attached: it names the
   * classes and members that couldn't be linked or initialized, not route data, as linking and
   * class initialization don't see the entry being encoded.
   */
  fun propertyFailure(type: Class<*>, error: Throwable) {
    when (error) {
      is DropArguments ->
        logOncePerType(type, PropertyFailure.OVER_LIMITS) {
          log(
            WARNING,
            "Nav3 dropped arguments for type %s (%s). Limits are $MAX_ARGUMENT_DEPTH levels, " +
              "$MAX_ARGUMENT_VALUES values, $MAX_ARGUMENT_CHARACTERS characters, and " +
              "$MAX_SERIALIZER_NESTING nested serializer calls. Map keys must stringify to " +
              "distinct strings. Use a custom BackStackEntryMapper to select fewer properties or " +
              "flatten nested values.",
            type.name,
            error.reason.serializedValue,
          )
        }
      is SerializationException ->
        logOncePerType(type, PropertyFailure.UNENCODABLE) {
          log(
            WARNING,
            "Nav3 couldn't encode properties for type %s. Recording names without arguments. " +
              "Contextual and open polymorphic properties and structured map keys aren't " +
              "supported. Use a custom BackStackEntryMapper to select supported properties or " +
              "extract arguments directly.",
            type.name,
          )
        }
      is LinkageError ->
        logOncePerType(type, PropertyFailure.UNLOADABLE) {
          log(
            WARNING,
            error,
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

  /** Runs [log] on the logger unless a [failure] of this kind was already logged for [type]. */
  private inline fun logOncePerType(
    type: Class<*>,
    failure: PropertyFailure,
    log: ILogger.() -> Unit,
  ) {
    if (loggedPropertyFailures.add(type to failure)) {
      logger().log()
    }
  }

  /** Why encoding an entry's properties failed, which determines the warning we log. */
  private enum class PropertyFailure {
    /** Encoding threw a [SerializationException], e.g., for a contextual property. */
    UNENCODABLE,

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

    /**
     * The oldest kotlinx.serialization version [KotlinxBackStackEntryMapper] supports.
     *
     * Must match `kotlinxSerializationFloor` in `gradle/nav3.versions.toml`.
     */
    const val MIN_KOTLINX_SERIALIZATION_VERSION: String = "1.7.3"

    const val INCOMPATIBLE_KOTLINX_ADVICE: String =
      "Requires kotlinx.serialization $MIN_KOTLINX_SERIALIZATION_VERSION or later. " +
        "Upgrade kotlinx.serialization or the Sentry SDK. If both are current, report this at " +
        "https://github.com/getsentry/sentry-java/issues/new/choose. Use a custom " +
        "BackStackEntryMapper as a workaround."
  }
}
