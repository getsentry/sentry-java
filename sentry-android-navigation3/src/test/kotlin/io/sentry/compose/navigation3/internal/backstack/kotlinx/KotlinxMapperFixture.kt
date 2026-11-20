package io.sentry.compose.navigation3.internal.backstack.kotlinx

import io.sentry.ILogger
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
 * A controllable [KotlinxCompatibilityProbe] that reports compatibility by default. Set
 * [nameLookupError] to fail the check, or [nameLookupInconclusive] to report it inconclusive.
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
 * [lookedUpTypes] are unused. With an `isAnnotatedSerializable` check, the mapper uses it instead
 * of reading each type's `@Serializable` annotation.
 */
internal class KotlinxMapperFixture
private constructor(
  useDefaultLookUp: Boolean,
  val probe: KotlinxCompatibilityProbe,
  isAnnotatedSerializable: ((Class<*>) -> Boolean)?,
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

  val mapper: KotlinxBackStackEntryMapper

  init {
    val lookUpSerializer: (Class<*>) -> KSerializer<*>? =
      if (useDefaultLookUp) {
        ::reflectiveSerializerOrNull
      } else {
        {
          lookedUpTypes += it
          lookUp(it)
        }
      }
    mapper =
      if (isAnnotatedSerializable == null) {
        KotlinxBackStackEntryMapper(lookUpSerializer, { logger }, probe)
      } else {
        KotlinxBackStackEntryMapper(
          lookUpSerializer,
          { logger },
          probe,
          isAnnotatedSerializable,
        )
      }
  }

  companion object {
    fun create(
      useDefaultLookUp: Boolean = false,
      probe: KotlinxCompatibilityProbe = FakeCompatibilityProbe(),
      isAnnotatedSerializable: ((Class<*>) -> Boolean)? = null,
    ) = KotlinxMapperFixture(useDefaultLookUp, probe, isAnnotatedSerializable)
  }
}
