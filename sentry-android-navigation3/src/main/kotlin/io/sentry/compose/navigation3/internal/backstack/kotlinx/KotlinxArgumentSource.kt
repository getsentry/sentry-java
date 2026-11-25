package io.sentry.compose.navigation3.internal.backstack.kotlinx

import io.sentry.compose.navigation3.internal.backstack.ArgumentBudget
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason.Companion.ARGUMENT_DROP_REASON_KEY
import io.sentry.compose.navigation3.internal.backstack.ArgumentSink
import io.sentry.compose.navigation3.internal.backstack.ArgumentSource
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.compose.navigation3.internal.backstack.TreeArgumentWriter
import io.sentry.util.ExceptionUtils
import kotlinx.serialization.KSerializer

/**
 * The arguments that `SerialNameAndProperties` returns for a back stack entry, encoded from the
 * entry's properties only when they're needed.
 *
 * **Why the arguments are encoded lazily**
 *
 * `SentryBackStackEntry.arguments` is a public `Map`, so the mapper must return one. But encoding
 * every entry's properties in the mapper would waste work: the `ArgumentSanitizer` spends one value
 * and character budget across all the entries of a back stack update, newest first, and once it's
 * spent, it drops older entries' arguments without reading them. Encoding lazily means those
 * entries are never encoded. (Measured on the JVM, this keeps an update's encoding cost roughly
 * constant as `maxCapturedBackStackEntries` grows, where encoding eagerly grows with it.)
 *
 * **Two ways to read the arguments**
 *
 * This class is both a [Map] and an [ArgumentSource], and which role is used depends on the reader:
 * - The SDK: `ArgumentSanitizer` checks whether an entry's arguments are an [ArgumentSource] and,
 *   if so, calls [writeTo] instead of reading them as a map. That encodes the properties straight
 *   into the update's sink, so they spend exactly the shared budget that the result uses, and a
 *   limit they break is reported against that budget.
 * - Anyone else: any read of the map (including `equals`, `hashCode`, and `toString`) encodes the
 *   properties once into a cached copy with a budget of its own. This happens, e.g., when an app's
 *   own mapper calls `SerialNameAndProperties` and filters or copies the result. The SDK then
 *   sanitizes whatever that mapper returns like any other map, against the shared budget.
 *
 * [writeTo] never uses the cached copy, even if one exists: it always encodes again, so the entry's
 * charges against the shared budget don't depend on whether something read the map first. Replaying
 * the copy would charge differently, e.g., for a `CharSequence` value whose `toString()` throws,
 * which direct encoding charges for both the failed value and the
 * [KotlinxPropertiesEncoder.UNENCODABLE_SUBSTITUTE] recorded in its place, while the copy holds
 * only the substitute. This only costs a second encoding when something reads the arguments and
 * then returns the same entry unchanged.
 *
 * If the copy's arguments are dropped, reading it as a map returns only the drop reason, under
 * [ARGUMENT_DROP_REASON_KEY]. An app's mapper that passes the map on passes the reason on as an
 * ordinary argument, so Sentry records it instead of recording no arguments and no reason.
 *
 * Both ways run the app's serializers, on the reader's thread. The copy is published with
 * [LazyThreadSafetyMode.PUBLICATION], so no lock is held while they run.
 *
 * **Failures**
 *
 * Values that the properties encoder records as [KotlinxPropertiesEncoder.UNENCODABLE_SUBSTITUTE]
 * are reported to [onUnencodableValue]; the rest of the entry's arguments are still recorded. Other
 * failures are reported to [onFailure], except for an argument limit broken while writing to the
 * `ArgumentSanitizer`'s sink, which the sanitizer reports instead, as whether a limit is broken
 * depends on what fraction of the [ArgumentBudget] the update's other entries spent.
 *
 * **Retention**
 *
 * Keeps the [backStackEntry] and its [serializer] for as long as this map is reachable. The SDK
 * doesn't keep these maps past a back stack update, but an app that keeps a mapper's result also
 * keeps its entry.
 */
internal class KotlinxArgumentSource(
  private val backStackEntry: Any,
  private val serializer: KSerializer<Any>,
  private val onUnencodableValue: () -> Unit = {},
  private val onFailure: (Throwable) -> Unit,
) : AbstractMap<String, Any?>(), ArgumentSource {

  // Readers that race on the first read may each encode, but all of them see the copy published
  // first.
  private val copy: Lazy<Map<String, Any?>> =
    lazy(LazyThreadSafetyMode.PUBLICATION) { encodeCopy() }

  override val entries: Set<Map.Entry<String, Any?>>
    get() = copy.value.entries

  /**
   * Encodes the entry's properties into [sink], without using the cached copy (see "Two ways to
   * read the arguments" above).
   *
   * Throws [DropArguments] if the arguments can't be written, either with the reason for the
   * constraint they violated, or with [ArgumentDropReason.ENCODING_FAILED] if encoding failed.
   */
  override fun writeTo(sink: ArgumentSink) {
    encode(sink)
  }

  @Suppress("ThrowsCount")
  private fun encode(sink: ArgumentSink) {
    try {
      KotlinxPropertiesEncoder.encode(serializer, backStackEntry, sink, onUnencodableValue)
    } catch (e: DropArguments) {
      sink.throwIfFailed()
      throw e
    } catch (e: LinkageError) {
      // Preserve the entry's name and drop its properties. The creation-time probe checks encoder
      // compatibility but can't exercise every path. This LinkageError may be specific to the
      // entry's serializers, so only this entry's arguments are dropped.
      sink.throwIfFailed()
      onFailure(e)
      throw DropArguments(ArgumentDropReason.ENCODING_FAILED, warning = null)
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
      // App serializers and map-key toString() calls may throw anything. Catch broadly to log once
      // per type.
      ExceptionUtils.rethrowIfFatal(t)
      sink.throwIfFailed()
      onFailure(t)
      throw DropArguments(ArgumentDropReason.ENCODING_FAILED, warning = null)
    }
  }

  /**
   * Returns the copy that map reads see: the encoded arguments, or, if they were dropped, only the
   * drop reason under [ARGUMENT_DROP_REASON_KEY].
   */
  private fun encodeCopy(): Map<String, Any?> {
    val sink = TreeArgumentWriter(ArgumentBudget())

    return try {
      encode(sink)
      sink.argumentsOrThrow()
    } catch (e: DropArguments) {
      // encode already reported an encoding failure.
      if (e.reason != ArgumentDropReason.ENCODING_FAILED) {
        onFailure(e)
      }
      mapOf(ARGUMENT_DROP_REASON_KEY to e.reason.serializedValue)
    }
  }
}

/**
 * The arguments of a back stack entry whose properties can't be encoded because the app's
 * kotlinx.serialization version doesn't support the properties encoder.
 *
 * Like [KotlinxArgumentSource], it's both a [Map] and an [ArgumentSource]. Map reads return
 * `{"dropped_by_sentry": "incompatible_kotlinx_serialization"}`, and writes drop the entry's
 * arguments with [ArgumentDropReason.INCOMPATIBLE_KOTLINX_SERIALIZATION], so that the recorded
 * reason points at the version rather than at the entry's own type.
 */
internal object KotlinxUnencodableArguments : AbstractMap<String, Any?>(), ArgumentSource {

  private val dropReason =
    mapOf(
      ARGUMENT_DROP_REASON_KEY to
        ArgumentDropReason.INCOMPATIBLE_KOTLINX_SERIALIZATION.serializedValue,
    )

  override val entries: Set<Map.Entry<String, Any?>>
    get() = dropReason.entries

  override fun writeTo(sink: ArgumentSink) {
    throw DropArguments(ArgumentDropReason.INCOMPATIBLE_KOTLINX_SERIALIZATION, warning = null)
  }
}
