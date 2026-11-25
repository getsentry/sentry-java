package io.sentry.compose.navigation3.internal.backstack.kotlinx

import io.sentry.util.ExceptionUtils
import java.lang.reflect.InvocationTargetException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializerOrNull

/**
 * Returns [type]'s serializer, or `null` if unavailable, including when type arguments are needed.
 * Extends [serializerOrNull] to support private `@Serializable` objects. Also supports built-in
 * types and unannotated enums.
 *
 * May execute serializer code and throw. Unwraps fatal causes such as [OutOfMemoryError] from
 * [InvocationTargetException] so that fatal-error checks can recognize them. Keeps [LinkageError]
 * causes wrapped because they affect only this type.
 */
@Suppress("SwallowedException")
internal fun reflectiveSerializerOrNull(type: Class<*>): KSerializer<*>? =
  try {
    try {
      serializerOrNull(type)
    } catch (_: IllegalAccessException) {
      // kotlinx.serialization reads a serializable object's INSTANCE field without making it
      // accessible, which will fail for private objects.
      privateObjectSerializerOrNull(type)
    }
  } catch (e: InvocationTargetException) {
    val cause = e.cause
    if (cause != null && cause !is LinkageError) {
      ExceptionUtils.rethrowIfFatal(cause)
    }
    throw e
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
