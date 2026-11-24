package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.ArgumentBudget
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.ArgumentSink
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.compose.navigation3.internal.backstack.TreeArgumentWriter
import kotlin.test.assertFailsWith
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Encoder

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

/**
 * Asserts that [block] throws [DropArguments] for [reason], with that reason's standard warning,
 * and returns it. The duplicate-key warning is checked too, so that it can't include the app's key
 * text.
 */
internal fun assertDropsArguments(reason: ArgumentDropReason, block: () -> Unit): DropArguments {
  val exception = assertFailsWith<DropArguments> { block() }

  assertThat(exception.reason).isEqualTo(reason)
  assertThat(exception.warning).isEqualTo(standardWarningFor(reason))
  return exception
}

private fun standardWarningFor(reason: ArgumentDropReason): String =
  when (reason) {
    ArgumentDropReason.CHARACTER_LIMIT -> DropArguments.MAX_CHARACTER_WARNING
    ArgumentDropReason.DUPLICATE_KEY -> DropArguments.DUPLICATE_KEY_WARNING
    ArgumentDropReason.INVALID_STRUCTURE -> DropArguments.STRUCTURE_WARNING
    ArgumentDropReason.MAX_COUNT -> DropArguments.MAX_COUNT_WARNING
    else -> error("$reason has no standard warning.")
  }

/**
 * Delegates to [delegate], recording each serialized value and throwing [error], if set, for the
 * values [failsFor] accepts.
 */
internal class RecordingSerializer<T>(private val delegate: KSerializer<T>) :
  KSerializer<T> by delegate {
  val serialized = mutableListOf<T>()
  var error: Throwable? = null
  var failsFor: (T) -> Boolean = { true }

  override fun serialize(encoder: Encoder, value: T) {
    serialized += value
    error?.takeIf { failsFor(value) }?.let { throw it }
    delegate.serialize(encoder, value)
  }
}
