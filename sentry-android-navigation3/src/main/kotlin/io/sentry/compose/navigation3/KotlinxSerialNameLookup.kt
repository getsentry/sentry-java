package io.sentry.compose.navigation3

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializerOrNull

/**
 * The entry point for this library's kotlinx.serialization code, a `compileOnly` dependency. Don't
 * touch it unless the library is known to be on the runtime classpath (see
 * [SerialNameBackStackEntryMapper]). [KotlinxArgumentsEncoder] is only reached through here.
 */
internal object KotlinxSerialNameLookup : SerialNameLookup {

  // Serializer lookups use reflection, so cache them per class, including failed lookups.
  private val serializers = ConcurrentHashMap<Class<*>, CachedSerializer>()

  override fun serialNameOrNull(type: Class<*>): String? =
    cachedSerializerOrNull(type)?.descriptor?.serialName

  // Encoding failures mean no arguments; the caller logs them once per class.
  @Suppress("SwallowedException")
  override fun argumentsOrNull(backStackEntry: Any): Map<String, Any?>? {
    val serializer = cachedSerializerOrNull(backStackEntry.javaClass) ?: return null
    return try {
      @Suppress("UNCHECKED_CAST")
      KotlinxArgumentsEncoder.encode(serializer as KSerializer<Any>, backStackEntry)
    } catch (e: SerializationException) {
      null
    }
  }

  private fun cachedSerializerOrNull(type: Class<*>): KSerializer<*>? =
    serializers.getOrPut(type) { CachedSerializer(lookUpSerializer(type)) }.serializer

  // The private-object fallback replaces the exception with a result.
  @Suppress("SwallowedException")
  private fun lookUpSerializer(type: Class<*>): KSerializer<*>? {
    // Only name classes the app marked @Serializable. kotlinx.serialization also provides
    // serializers for built-in types (e.g. String, List) and unannotated enums. Their serial names
    // would either be misleading ("kotlin.String") or derived from an obfuscated class name.
    if (!type.isAnnotationPresent(Serializable::class.java)) {
      return null
    }

    return try {
      serializerOrNull(type)
    } catch (e: IllegalAccessException) {
      // kotlinx.serialization reads a serializable object's INSTANCE field without making it
      // accessible, which fails for private objects.
      privateObjectSerializerOrNull(type)
    }
  }

  /**
   * Looks up the serializer of a private `@Serializable object` through the members the
   * serialization compiler plugin generates for it. kotlinx.serialization's consumer R8 rules keep
   * both members.
   */
  // A failed lookup means the name is unknown; the caller logs it once per class.
  @Suppress("SwallowedException")
  private fun privateObjectSerializerOrNull(type: Class<*>): KSerializer<*>? =
    try {
      val instance = type.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
      type.getDeclaredMethod("serializer").apply { isAccessible = true }.invoke(instance)
        as? KSerializer<*>
    } catch (e: ReflectiveOperationException) {
      null
    }

  /** Wraps a nullable serializer, since [ConcurrentHashMap] doesn't accept `null` values. */
  private class CachedSerializer(val serializer: KSerializer<*>?)
}
