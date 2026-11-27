package io.sentry.compose.navigation3.internal.backstack.kotlinx

import io.sentry.ILogger
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.ArgumentBudget
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.ArgumentSource
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.compose.navigation3.internal.backstack.TreeArgumentWriter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import org.mockito.AdditionalAnswers.delegatesTo
import org.mockito.Mockito
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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

/**
 * Serializes [Detail] with a descriptor whose shape, its `elementsCount` and `kind`, throws
 * [elementsCountError] and [kindError] when set. Counts every read of the shape in [shapeReads].
 * Encoding reads the descriptor's `kind`, so it throws [kindError] too.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class ShapeThrowingDetailSerializer(
  private val elementsCountError: Throwable? = null,
  private val kindError: Throwable? = null,
) : KSerializer<Detail> {
  var shapeReads = 0

  private val delegate = Detail.serializer().descriptor

  override val descriptor: SerialDescriptor =
    Mockito.mock(SerialDescriptor::class.java, delegatesTo<Any>(delegate)).apply {
      doAnswer {
          shapeReads++
          elementsCountError?.let { error -> throw error }
          delegate.elementsCount
        }
        .whenever(this)
        .elementsCount
      doAnswer {
          shapeReads++
          kindError?.let { error -> throw error }
          delegate.kind
        }
        .whenever(this)
        .kind
    }

  override fun serialize(encoder: Encoder, value: Detail) {
    encoder.encodeStructure(descriptor) { encodeStringElement(descriptor, 0, value.id) }
  }

  override fun deserialize(decoder: Decoder): Detail = throw UnsupportedOperationException()
}

/**
 * A [KotlinxCompatibilityProbe] that finds the app's kotlinx.serialization version compatible
 * unless [nameLookupError] or [encodingError] is set, so that mapper tests don't depend on the real
 * probe. Reports the name check inconclusive with [nameLookupInconclusive], if set, and the
 * encoding check inconclusive with [encodingInconclusiveError], if set. Counts the checks of the
 * properties encoder in [encodingChecks].
 */
internal class FakeCompatibilityProbe(
  private val nameLookupError: LinkageError? = null,
  private val nameLookupInconclusive: Inconclusive? = null,
) : KotlinxCompatibilityProbe {

  override fun nameLookupLinkageError(onInconclusive: (Throwable?) -> Unit): LinkageError? {
    nameLookupError?.let {
      return it
    }
    nameLookupInconclusive?.let { onInconclusive(it.error) }
    return null
  }

  /** Why the name check is inconclusive: [error], or `null` if a probe serializer wasn't found. */
  class Inconclusive(val error: Throwable?)
}

/**
 * A [KotlinxBackStackEntryMapper] whose serializer lookup returns [serializers] by default. The
 * mapper only looks up `@Serializable` types, so [lookUp] never sees any others. Its compatibility
 * [probe] finds the app's kotlinx.serialization version compatible by default.
 *
 * With `useDefaultLookUp = true`, the mapper uses its real serializer lookup, so [lookUp] and
 * [lookedUpTypes] are unused.
 */
internal class KotlinxMapperFixture
private constructor(
  useDefaultLookUp: Boolean,
  val probe: KotlinxCompatibilityProbe,
) {
  val logger = mock<ILogger>()

  val detailSerializer = RecordingSerializer(Detail.serializer())
  val searchSerializer = RecordingSerializer(Search.serializer())
  val serializers: Map<Class<*>, KSerializer<*>> =
    mapOf(
      Home::class.java to Home.serializer(),
      Detail::class.java to detailSerializer,
      Search::class.java to searchSerializer,
      WithContextual::class.java to WithContextual.serializer(),
    )

  val lookedUpTypes = mutableListOf<Class<*>>()
  var lookUp: (Class<*>) -> KSerializer<*>? = { serializers[it] }

  val mapper =
    if (useDefaultLookUp) {
      KotlinxBackStackEntryMapper(logger = { logger }, probe = probe)
    } else {
      KotlinxBackStackEntryMapper(
        lookUpSerializer = {
          lookedUpTypes += it
          lookUp(it)
        },
        logger = { logger },
        probe = probe,
      )
    }

  companion object {
    fun create(
      useDefaultLookUp: Boolean = false,
      probe: KotlinxCompatibilityProbe = FakeCompatibilityProbe(),
    ) = KotlinxMapperFixture(useDefaultLookUp, probe)
  }
}

/** The arguments read from an entry whose arguments were dropped for [reason]. */
internal fun droppedBy(reason: ArgumentDropReason): Map<String, Any?> =
  mapOf(ArgumentDropReason.ARGUMENT_DROP_REASON_KEY to reason.serializedValue)

/** Maps [entry] and reads its arguments, which encodes its properties. */
internal fun KotlinxBackStackEntryMapper.mapAndReadArguments(entry: Any): SentryBackStackEntry? =
  map(entry)?.also { it.arguments?.size }

/**
 * Maps [entry] and writes its arguments to a sink with a budget of its own, as the argument
 * sanitizer does, which encodes its properties. Ignores the [DropArguments] a failure throws.
 */
internal fun KotlinxBackStackEntryMapper.mapAndWriteArguments(entry: Any) {
  val arguments = map(entry)?.arguments as ArgumentSource
  try {
    arguments.writeTo(TreeArgumentWriter(ArgumentBudget()))
  } catch (_: DropArguments) {}
}
