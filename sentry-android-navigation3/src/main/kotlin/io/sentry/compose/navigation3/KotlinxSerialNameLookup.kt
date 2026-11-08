package io.sentry.compose.navigation3

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializerOrNull

/**
 * The entry point for this library's kotlinx.serialization code, a `compileOnly` dependency. Don't
 * touch it unless the library is known to be on the runtime classpath (see
 * [SerialNameBackStackEntryMapper]).
 *
 * Lookups use reflection and aren't cached here; [SerialNameBackStackEntryMapper] caches the result
 * per class.
 */
internal object KotlinxSerialNameLookup : SerialNameLookup {

  override fun serialNameOrNull(type: Class<*>): String? =
    lookUpSerializer(type)?.descriptor?.serialName

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
}
