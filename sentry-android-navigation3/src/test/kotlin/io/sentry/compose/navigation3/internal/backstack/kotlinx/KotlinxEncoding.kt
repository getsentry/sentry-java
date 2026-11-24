package io.sentry.compose.navigation3.internal.backstack.kotlinx

import io.sentry.compose.navigation3.internal.backstack.ArgumentBudget
import io.sentry.compose.navigation3.internal.backstack.ArgumentSink
import io.sentry.compose.navigation3.internal.backstack.TreeArgumentWriter
import kotlinx.serialization.KSerializer

/**
 * Encodes the properties of [value] with [serializer] into a map, with a budget of its own. Throws
 * as [KotlinxPropertiesEncoder.encode] does.
 */
internal fun <T : Any> encodeToMap(
  serializer: KSerializer<T>,
  value: T,
  onUnencodableValue: (IllegalArgumentException) -> Unit = {},
): Map<String, Any?> {
  val sink = TreeArgumentWriter(ArgumentBudget())
  encodeInto(serializer, value, sink, onUnencodableValue)
  return sink.argumentsOrThrow()
}

/** Encodes the properties of [value] with [serializer] into [sink]. */
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> encodeInto(
  serializer: KSerializer<T>,
  value: T,
  sink: ArgumentSink,
  onUnencodableValue: (IllegalArgumentException) -> Unit = {},
) {
  KotlinxPropertiesEncoder.encode(serializer as KSerializer<Any>, value, sink, onUnencodableValue)
}
