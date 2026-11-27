package io.sentry.compose.navigation3.internal.backstack.kotlinx

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializerOrNull

/**
 * Returns the serializer of [type], or `null` if kotlinx.serialization can't provide one (e.g. for
 * a generic class whose serializer needs type arguments).
 *
 * Unlike kotlinx.serialization's [serializerOrNull], this also finds the serializers of private
 * `@Serializable` objects. Like it, this returns serializers for built-in types (e.g. `String`) and
 * enums that aren't `@Serializable`.
 *
 * This method may throw, as looking up a serializer can run the type's serializer code for the
 * first time (e.g. an [InvocationTargetException][java.lang.reflect.InvocationTargetException]
 * whose cause is the serializer initializer's error).
 */
@Suppress("SwallowedException")
internal fun reflectiveSerializerOrNull(type: Class<*>): KSerializer<*>? =
  try {
    serializerOrNull(type)
  } catch (_: IllegalAccessException) {
    // kotlinx.serialization reads a serializable object's INSTANCE field without making it
    // accessible, which fails for private objects.
    privateObjectSerializerOrNull(type)
  }

/**
 * Uses reflection to look up the serializer of a private `@Serializable object` through the members
 * the serialization compiler plugin generates for it. (kotlinx.serialization's consumer R8 rules
 * keep both members.)
 *
 * Propagates any exception thrown by the reflective lookup.
 */
private fun privateObjectSerializerOrNull(type: Class<*>): KSerializer<*>? {
  val instance = type.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
  return type.getDeclaredMethod("serializer").apply { isAccessible = true }.invoke(instance)
    as? KSerializer<*>
}
