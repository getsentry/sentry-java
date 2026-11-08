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
 * [serializer] needed to encode them. Encoding is performed on demand and cached in order to:
 *
 * - guarantee a single, consistent snapshot for all consumers; and
 * - avoid doing work for arguments that will be dropped or never read.
 *
 * If the arguments are dropped, reading them as a map returns only the drop reason.
 *
 * In general, all failures are reported to [onFailure]. The sole exception is an argument
 * constraint violated while writing to the `ArgumentSanitizer`'s sink. The sanitizer reports that
 * instead, as those limits can depend on what fraction of the [ArgumentBudget] the update's other
 * entries spent.
 */
internal class KotlinxArguments(
  private val backStackEntry: Any,
  private val serializer: KSerializer<Any>,
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
   * Writes the entry's arguments to [sink], encoding its properties if needed. If available, uses
   * the previously cached copy instead of re-encoding [sink].
   *
   * Throws [DropArguments] if the arguments can't be written, either with the reason for the
   * constraint they violated, or with [ArgumentDropReason.ENCODING_FAILED] if encoding failed.
   */
  override fun writeTo(sink: ArgumentSink) {
    if (copy.isInitialized()) {
      copy.value.writeTo(sink)
    } else {
      encode(sink)
    }
  }

  @Suppress("ThrowsCount")
  private fun encode(sink: ArgumentSink) {
    try {
      KotlinxPropertiesEncoder.encode(serializer, backStackEntry, sink)
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
      EncodedCopy(sink.argumentsOrThrow(), failure = null)
    } catch (e: DropArguments) {
      // encode already reported an encoding failure.
      if (e.reason != ArgumentDropReason.ENCODING_FAILED) {
        onFailure(e)
      }
      EncodedCopy(mapOf(ARGUMENT_DROP_REASON_KEY to e.reason.serializedValue), failure = e)
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
     * The exception that dropped the back stack entry's arguments, whether because of an encoding
     * failure or a violated argument constraint. `null` if encoding succeeded.
     *
     * When set, [writeTo] re-throws this failure so that the owner of the [ArgumentBudget] can drop
     * the entry's arguments for the same reason.
     */
    val failure: DropArguments?,
  ) : ArgumentSource {

    override fun writeTo(sink: ArgumentSink) {
      failure?.let { throw it }
      // The copy holds only plain maps, lists, and values the sink supports.
      HostArgumentSource(arguments, onUnsupportedValue = {}).writeTo(sink)
    }
  }
}

/**
 * The arguments of a back stack entry whose properties can't be encoded because the app's
 * kotlinx.serialization version doesn't support the properties encoder.
 *
 * Handles encoding failures like [KotlinxArguments]: map reads return `{"dropped_by_sentry":
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
