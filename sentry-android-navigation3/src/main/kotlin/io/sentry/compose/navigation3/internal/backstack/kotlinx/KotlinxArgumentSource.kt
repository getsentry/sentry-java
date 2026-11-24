package io.sentry.compose.navigation3.internal.backstack.kotlinx

import io.sentry.compose.navigation3.internal.backstack.ArgumentBudget
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason.Companion.ARGUMENT_DROP_REASON_KEY
import io.sentry.compose.navigation3.internal.backstack.ArgumentSink
import io.sentry.compose.navigation3.internal.backstack.ArgumentSource
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.compose.navigation3.internal.backstack.HostArgumentSource
import io.sentry.compose.navigation3.internal.backstack.TreeArgumentWriter
import io.sentry.util.ExceptionUtils
import kotlinx.serialization.KSerializer

/**
 * The [ArgumentSource] for our built-in `BackStackEntryMapper`s.
 *
 * It keeps a reference to the [backStackEntry] whose properties it reads, as well as the
 * [serializer] needed to encode them, for as long as the map itself is reachable. (The SDK doesn't
 * retain these maps past a back stack update, but a host app that keeps a mapper's result also
 * keeps its entry.)
 *
 * Encoding is performed on demand and cached in order to:
 *
 * - guarantee a single, consistent snapshot for all consumers; and
 * - avoid doing work for arguments that will be dropped or never read.
 *
 * If the arguments are dropped, reading them as a map returns only the drop reason.
 *
 * Values that the properties encoder records as [KotlinxPropertiesEncoder.UNENCODABLE_SUBSTITUTE]
 * are reported to [onUnencodableValue]; the rest of the entry's arguments are still recorded.
 *
 * In general, all failures are reported to [onFailure]. The sole exception is an argument
 * constraint violated while writing to the `ArgumentSanitizer`'s sink. The sanitizer reports that
 * instead, as those limits can depend on what fraction of the [ArgumentBudget] the update's other
 * entries spent.
 */
internal class KotlinxArgumentSource(
  private val backStackEntry: Any,
  private val serializer: KSerializer<Any>,
  private val onUnencodableValue: (IllegalArgumentException) -> Unit = {},
  private val onFailure: (Throwable) -> Unit,
) : AbstractMap<String, Any?>(), ArgumentSource {

  private val copy: Lazy<EncodedCopy> =
    // PUBLICATION so that no lock is held while encoding runs the host app's serializers, which
    // could otherwise deadlock on a lock held by the app. Readers that race on the first read may
    // each encode, but all of them see the copy published first.
    lazy(LazyThreadSafetyMode.PUBLICATION) { encodeCopy() }

  override val entries: Set<Map.Entry<String, Any?>>
    get() = copy.value.arguments.entries

  /**
   * Writes the entry's arguments to [sink], encoding its properties if needed. Uses the previously
   * cached copy instead if it was encoded successfully.
   *
   * Throws [DropArguments] if the arguments can't be written, either with the reason for the
   * constraint they violated, or with [ArgumentDropReason.ENCODING_FAILED] if encoding failed.
   */
  override fun writeTo(sink: ArgumentSink) {
    val cached = if (copy.isInitialized()) copy.value else null
    if (cached != null && !cached.isDropped) {
      cached.writeTo(sink)
    } else {
      // A failed copy used a separate budget. Encode again so values written before the failure
      // consume the update's shared budget; rethrowing the cached failure would leave extra room
      // for older entries' arguments.
      encode(sink)
    }
  }

  @Suppress("ThrowsCount")
  private fun encode(sink: ArgumentSink) {
    try {
      KotlinxPropertiesEncoder.encode(serializer, backStackEntry, sink, onUnencodableValue)
    } catch (e: DropArguments) {
      // Prefer the sink's first failure because it determines whether older entries keep their
      // arguments.
      throw sink.failure ?: e
    } catch (e: LinkageError) {
      // Preserve the entry's name and drop its properties. The creation-time probe checks encoder
      // compatibility but can't exercise every path. This LinkageError may be specific to the
      // entry's serializers, so we keep the failure local to this type.
      sink.failure?.let { throw it }
      onFailure(e)
      throw DropArguments(ArgumentDropReason.ENCODING_FAILED, warning = null)
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
      // App serializers and map-key toString() calls may throw anything. Catch broadly to log once
      // per type.
      ExceptionUtils.rethrowIfFatal(t)

      // A serializer may replace a constraint violation with its own exception. Prefer the sink's
      // recorded failure because it determines whether older entries keep their arguments.
      sink.failure?.let { throw it }
      onFailure(t)
      throw DropArguments(ArgumentDropReason.ENCODING_FAILED, warning = null)
    }
  }

  private fun encodeCopy(): EncodedCopy {
    val sink = TreeArgumentWriter(ArgumentBudget())

    return try {
      encode(sink)
      EncodedCopy(sink.argumentsOrThrow(), isDropped = false)
    } catch (e: DropArguments) {
      // encode already reported an encoding failure.
      if (e.reason != ArgumentDropReason.ENCODING_FAILED) {
        onFailure(e)
      }
      EncodedCopy(mapOf(ARGUMENT_DROP_REASON_KEY to e.reason.serializedValue), isDropped = true)
    }
  }

  /** Cached arguments encoded from a single back stack entry's properties. */
  private class EncodedCopy(
    /**
     * The encoded arguments, or, if they were dropped, only the drop reason under
     * [ARGUMENT_DROP_REASON_KEY].
     *
     * A host app mapper that copies or filters a dropped entry's arguments passes the reason on as
     * an ordinary argument. Sentry then records it as it would for this mapper's own output, rather
     * than recording no arguments and no reason.
     */
    val arguments: Map<String, Any?>,
    /**
     * Whether the back stack entry's arguments were dropped, because of an encoding failure or a
     * violated argument constraint.
     */
    val isDropped: Boolean,
  ) : ArgumentSource {

    /** Writes the encoded arguments to [sink]. Only for a copy that wasn't [dropped][isDropped]. */
    override fun writeTo(sink: ArgumentSink) {
      check(!isDropped) { "A dropped copy is encoded again instead of written." }
      // The copy holds only plain maps, lists, and values the sink supports.
      HostArgumentSource(arguments, onUnsupportedValue = {}).writeTo(sink)
    }
  }
}

/**
 * The arguments of a back stack entry whose properties can't be encoded because the app's
 * kotlinx.serialization version doesn't support the properties encoder.
 *
 * Handles encoding failures like [KotlinxArgumentSource]: map reads return `{"dropped_by_sentry":
 * "encoding_failed"}`, and writes drop the entry's arguments with
 * [ArgumentDropReason.ENCODING_FAILED].
 */
internal object KotlinxUnencodableArguments : AbstractMap<String, Any?>(), ArgumentSource {

  private val dropReason =
    mapOf(ARGUMENT_DROP_REASON_KEY to ArgumentDropReason.ENCODING_FAILED.serializedValue)

  override val entries: Set<Map.Entry<String, Any?>>
    get() = dropReason.entries

  override fun writeTo(sink: ArgumentSink) {
    throw DropArguments(ArgumentDropReason.ENCODING_FAILED, warning = null)
  }
}
