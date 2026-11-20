package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.ArgumentBudget
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.compose.navigation3.internal.backstack.TreeArgumentWriter
import io.sentry.compose.navigation3.internal.backstack.budgetWithCharactersRemaining
import io.sentry.compose.navigation3.internal.backstack.budgetWithValuesRemaining
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.Test

class KotlinxArgumentSourceTest {

  @Serializable data class Values(val values: List<Int>)

  @Serializable data class MutableDetail(var id: String)

  /**
   * Delegates to [delegate], but wraps a [DropArguments] it throws in an exception of its own, as a
   * serializer that catches and rethrows everything might.
   */
  private class WrappingSerializer<T>(
    private val delegate: KSerializer<T>,
    private val wrap: (DropArguments) -> Throwable,
  ) : KSerializer<T> by delegate {
    override fun serialize(encoder: Encoder, value: T) {
      try {
        delegate.serialize(encoder, value)
      } catch (e: DropArguments) {
        throw wrap(e)
      }
    }
  }

  /**
   * Writes [text], recording a [DropArguments] that throws in [caught], then encodes the link with
   * [LinkSerializer], so a chain long enough breaks the encoder's nesting limit after the sink's.
   */
  private class NestsAfterCaughtLimitSerializer(private val text: String) : KSerializer<Link> {
    var caught: DropArguments? = null

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("test.NestsAfterCaughtLimit", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Link) {
      try {
        encoder.encodeString(text)
      } catch (e: DropArguments) {
        caught = e
      }
      encoder.encodeSerializableValue(LinkSerializer, value)
    }

    override fun deserialize(decoder: Decoder): Link = throw UnsupportedOperationException()
  }

  /**
   * Encodes a [Detail] whose id is the number of this serializer's call, so that each encoding of
   * an entry differs. Runs [beforeEncoding] with that number first.
   */
  private class NumberingDetailSerializer(private val beforeEncoding: (call: Int) -> Unit) :
    KSerializer<Detail> {
    private val calls = AtomicInteger()

    override val descriptor: SerialDescriptor = Detail.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Detail) {
      val call = calls.incrementAndGet()
      beforeEncoding(call)
      Detail.serializer().serialize(encoder, Detail("encoding-$call"))
    }

    override fun deserialize(decoder: Decoder): Detail = throw UnsupportedOperationException()
  }

  /** Delegates to [delegate], then throws [error], as a serializer that fails late might. */
  private class ThrowsAfterEncodingSerializer<T>(private val delegate: KSerializer<T>) :
    KSerializer<T> by delegate {
    val error = IllegalStateException("thrown after encoding")

    override fun serialize(encoder: Encoder, value: T) {
      delegate.serialize(encoder, value)
      throw error
    }
  }

  private val failures = mutableListOf<Throwable>()

  private val detailSerializer = RecordingSerializer(Detail.serializer())
  private val mutableDetailSerializer = RecordingSerializer(MutableDetail.serializer())

  /** A value over the argument character limit. */
  private val overLimitId = "x".repeat(MAX_ARGUMENT_CHARACTERS + 1)

  private val encodingFailed = droppedBy(ArgumentDropReason.ENCODING_FAILED)

  /** A link chain whose serializers nest one call more than the encoder allows. */
  private val tooDeeplyLinked = linked(length = KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING + 1)

  // Reading as a map

  @Test
  fun `reads as the entry's encoded properties`() {
    val source = argumentSourceOf(detailSerializer, Detail("1"))

    assertThat(source.entries.associate { it.key to it.value }).isEqualTo(mapOf("id" to "1"))
  }

  @Test
  fun `doesn't encode the entry until its arguments are read`() {
    argumentSourceOf(detailSerializer, Detail("1"))

    assertThat(detailSerializer.serialized).isEmpty()
  }

  @Test
  fun `encodes the entry when its arguments are first read through hashCode()`() {
    val source = argumentSourceOf(detailSerializer, Detail("1"))

    val hashCode = source.hashCode()

    assertThat(hashCode).isEqualTo(mapOf("id" to "1").hashCode())
    assertThat(detailSerializer.serialized).containsExactly(Detail("1"))
  }

  @Test
  fun `encodes the entry once across repeated reads`() {
    val source = argumentSourceOf(detailSerializer, Detail("1"))

    source.entries
    source.size
    source["id"]
    source.containsKey("id")
    source.equals(mapOf("id" to "1"))

    assertThat(detailSerializer.serialized).containsExactly(Detail("1"))
  }

  @Test
  fun `equals a plain map with the same entries`() {
    val source = argumentSourceOf(detailSerializer, Detail("1"))

    assertThat(source).isEqualTo(mapOf("id" to "1"))
    assertThat(mapOf("id" to "1")).isEqualTo(source)
    assertThat(source.hashCode()).isEqualTo(mapOf("id" to "1").hashCode())
  }

  @Test
  fun `doesn't equal a plain map with other entries`() {
    val source = argumentSourceOf(detailSerializer, Detail("1"))

    assertThat(source).isNotEqualTo(mapOf("id" to "2"))
    assertThat(mapOf("id" to "2")).isNotEqualTo(source)
  }

  @Test
  fun `reads each instance's arguments with a budget of its own`() {
    // Each entry has 151 values, so two entries wouldn't fit in one budget.
    val first = argumentSourceOf(Values.serializer(), Values(List(150) { it }))
    val second = argumentSourceOf(Values.serializer(), Values(List(150) { it }))

    assertThat(first).isEqualTo(mapOf("values" to List(150) { it }))
    assertThat(second).isEqualTo(mapOf("values" to List(150) { it }))
  }

  // Reading from several threads

  @Test
  fun `doesn't block a read on another thread's encoding that waits for a lock the reader holds`() {
    // Models a deadlock: the reader holds a lock of the app's, and the serializer encoding on
    // another thread needs it.
    val appLock = ReentrantLock()
    val encodingStarted = CountDownLatch(1)
    var otherThreadGotLock = false
    val serializer = NumberingDetailSerializer { call ->
      if (call == 1) {
        encodingStarted.countDown()
        otherThreadGotLock = appLock.tryLock(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (otherThreadGotLock) appLock.unlock()
      }
    }
    val source = argumentSourceOf(serializer, Detail("1"))
    appLock.lock()
    val otherRead = readOnAnotherThread(source)
    assertThat(encodingStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()

    val read =
      try {
        source.toMap()
      } finally {
        appLock.unlock()
      }

    assertThat(otherRead.await()).isEqualTo(read)
    assertThat(otherThreadGotLock).isTrue()
  }

  @Test
  fun `reads the copy published first on every thread when reads race on the first read`() {
    val encodingStarted = CountDownLatch(1)
    val firstReadDone = CountDownLatch(1)
    var otherThreadSawFirstRead = false
    val serializer = NumberingDetailSerializer { call ->
      // The other thread's encoding, the first call, waits until this thread's read returns.
      if (call == 1) {
        encodingStarted.countDown()
        otherThreadSawFirstRead = firstReadDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      }
    }
    val source = argumentSourceOf(serializer, Detail("1"))
    val otherRead = readOnAnotherThread(source)
    assertThat(encodingStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()

    val read = source.toMap()
    firstReadDone.countDown()

    assertThat(read).isEqualTo(mapOf("id" to "encoding-2"))
    assertThat(otherRead.await()).isEqualTo(read)
    assertThat(source.toMap()).isEqualTo(read)
    assertThat(otherThreadSawFirstRead).isTrue()
  }

  // Reading failures

  @Test
  fun `reads as only the broken limit when the entry breaks an argument limit`() {
    val source = argumentSourceOf(detailSerializer, Detail(overLimitId))

    assertThat(source).isEqualTo(droppedBy(ArgumentDropReason.CHARACTER_LIMIT))
  }

  @Test
  fun `reports the broken limit to onFailure when read`() {
    val source = argumentSourceOf(detailSerializer, Detail(overLimitId))

    source.size

    assertThat(failures).hasSize(1)
    assertThat((failures.single() as DropArguments).reason)
      .isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
  }

  @Test
  fun `reads as only the encoding failure and reports a serializer's exception once across reads`() {
    val error = IllegalStateException("boom")
    detailSerializer.error = error
    val source = argumentSourceOf(detailSerializer, Detail("1"))

    val reads = List(3) { source.toMap() }

    assertThat(reads).containsExactly(encodingFailed, encodingFailed, encodingFailed)
    assertThat(failures).containsExactly(error)
  }

  @Test
  fun `reads as only the encoding failure and reports a serializer's LinkageError once across reads`() {
    val error = NoClassDefFoundError("missing")
    detailSerializer.error = error
    val source = argumentSourceOf(detailSerializer, Detail("1"))

    val reads = List(3) { source.toMap() }

    assertThat(reads).containsExactly(encodingFailed, encodingFailed, encodingFailed)
    assertThat(failures).containsExactly(error)
  }

  @Test
  fun `reads as only the broken limit and reports it when a serializer replaced it with its own exception`() {
    val serializer = WrappingSerializer(Detail.serializer()) { SerializationException("replaced") }
    val source = argumentSourceOf(serializer, Detail(overLimitId))

    val read = source.toMap()

    assertThat(read).isEqualTo(droppedBy(ArgumentDropReason.CHARACTER_LIMIT))
    assertThat(failures).hasSize(1)
    assertThat((failures.single() as DropArguments).reason)
      .isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
  }

  @Test
  fun `reads as only the nesting limit and reports it when a serializer replaced it with its own exception`() {
    val serializer = WrappingSerializer(LinkSerializer) { SerializationException("replaced") }
    val source = argumentSourceOf(serializer, tooDeeplyLinked)

    val read = source.toMap()

    assertThat(read).isEqualTo(droppedBy(ArgumentDropReason.INVALID_STRUCTURE))
    assertThat(failures).hasSize(1)
    assertThat((failures.single() as DropArguments).reason)
      .isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
  }

  @Test
  fun `reads as only the sink's limit and reports it when the encoder's nesting limit follows it`() {
    val serializer = NestsAfterCaughtLimitSerializer(overLimitId)
    val source =
      argumentSourceOf(serializer, linked(length = KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING))

    val read = source.toMap()

    assertThat(read).isEqualTo(droppedBy(ArgumentDropReason.CHARACTER_LIMIT))
    assertThat(failures).hasSize(1)
    assertThat((failures.single() as DropArguments).reason)
      .isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
  }

  @Test
  fun `propagates fatal errors when read, without reporting them`() {
    val error = OutOfMemoryError()
    detailSerializer.error = error
    val source = argumentSourceOf(detailSerializer, Detail("1"))

    val thrown = assertFailsWith<OutOfMemoryError> { source.size }

    assertThat(thrown).isSameInstanceAs(error)
    assertThat(failures).isEmpty()
  }

  // writeTo()

  @Test
  fun `writeTo() - writes the entry's properties to the sink`() {
    val sink = TreeArgumentWriter(ArgumentBudget())

    argumentSourceOf(detailSerializer, Detail("1")).writeTo(sink)

    assertThat(sink.argumentsOrThrow()).isEqualTo(mapOf("id" to "1"))
  }

  @Test
  fun `writeTo() - encodes the entry each time it's written`() {
    val source = argumentSourceOf(detailSerializer, Detail("1"))

    source.writeTo(TreeArgumentWriter(ArgumentBudget()))
    source.writeTo(TreeArgumentWriter(ArgumentBudget()))

    assertThat(detailSerializer.serialized).containsExactly(Detail("1"), Detail("1"))
  }

  @Test
  fun `writeTo() - spends the sink's budget`() {
    // Each entry has 151 values, so two entries don't fit in one budget.
    val budget = ArgumentBudget()
    argumentSourceOf(Values.serializer(), Values(List(150) { it }))
      .writeTo(TreeArgumentWriter(budget))
    val second = argumentSourceOf(Values.serializer(), Values(List(150) { it }))

    val thrown = assertFailsWith<DropArguments> { second.writeTo(TreeArgumentWriter(budget)) }

    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.MAX_COUNT)
  }

  @Test
  fun `writeTo() - propagates a broken limit unchanged, without reporting it`() {
    val sink = TreeArgumentWriter(budgetWithValuesRemaining(0))

    val thrown =
      assertFailsWith<DropArguments> {
        argumentSourceOf(detailSerializer, Detail("1")).writeTo(sink)
      }

    assertThat(thrown).isSameInstanceAs(assertFailsWith<DropArguments> { sink.argumentsOrThrow() })
    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.MAX_COUNT)
    assertThat(failures).isEmpty()
  }

  @Test
  fun `writeTo() - reports a serializer's exception and drops the arguments as an unlogged encoding failure`() {
    val error = IllegalStateException("boom")
    detailSerializer.error = error

    val thrown =
      assertFailsWith<DropArguments> {
        argumentSourceOf(detailSerializer, Detail("1"))
          .writeTo(TreeArgumentWriter(ArgumentBudget()))
      }

    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.ENCODING_FAILED)
    assertThat(thrown.warning).isNull()
    assertThat(failures).containsExactly(error)
  }

  @Test
  fun `writeTo() - reports a serializer's LinkageError and drops the arguments as an unlogged encoding failure`() {
    val error = AbstractMethodError()
    detailSerializer.error = error

    val thrown =
      assertFailsWith<DropArguments> {
        argumentSourceOf(detailSerializer, Detail("1"))
          .writeTo(TreeArgumentWriter(ArgumentBudget()))
      }

    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.ENCODING_FAILED)
    assertThat(thrown.warning).isNull()
    assertThat(failures).containsExactly(error)
  }

  @Test
  fun `writeTo() - propagates fatal errors without reporting them`() {
    val error = OutOfMemoryError()
    detailSerializer.error = error

    val thrown =
      assertFailsWith<OutOfMemoryError> {
        argumentSourceOf(detailSerializer, Detail("1"))
          .writeTo(TreeArgumentWriter(ArgumentBudget()))
      }

    assertThat(thrown).isSameInstanceAs(error)
    assertThat(failures).isEmpty()
  }

  @Test
  fun `writeTo() - rethrows a broken limit that a serializer wrapped, without reporting it`() {
    val serializer =
      WrappingSerializer(Detail.serializer()) { SerializationException("wrapped", it) }
    val sink = TreeArgumentWriter(budgetWithValuesRemaining(0))

    val thrown =
      assertFailsWith<DropArguments> {
        argumentSourceOf(serializer, Detail("1")).writeTo(sink)
      }

    assertThat(thrown).isSameInstanceAs(sink.failure)
    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.MAX_COUNT)
    assertThat(failures).isEmpty()
  }

  @Test
  fun `writeTo() - rethrows the sink's limit that a serializer replaced with its own exception, unreported`() {
    val serializer = WrappingSerializer(Detail.serializer()) { SerializationException("replaced") }
    val sink = TreeArgumentWriter(ArgumentBudget())

    val thrown =
      assertFailsWith<DropArguments> {
        argumentSourceOf(serializer, Detail(overLimitId)).writeTo(sink)
      }

    assertThat(thrown).isSameInstanceAs(sink.failure)
    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(failures).isEmpty()
  }

  @Test
  fun `writeTo() - rethrows the sink's limit that a serializer replaced with a LinkageError, unreported`() {
    val serializer = WrappingSerializer(Detail.serializer()) { NoClassDefFoundError("replaced") }
    val sink = TreeArgumentWriter(ArgumentBudget())

    val thrown =
      assertFailsWith<DropArguments> {
        argumentSourceOf(serializer, Detail(overLimitId)).writeTo(sink)
      }

    assertThat(thrown).isSameInstanceAs(sink.failure)
    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(failures).isEmpty()
  }

  @Test
  fun `writeTo() - rethrows the nesting limit that a serializer replaced with its own exception, unreported`() {
    val serializer = WrappingSerializer(LinkSerializer) { SerializationException("replaced") }
    val sink = TreeArgumentWriter(ArgumentBudget())

    val thrown =
      assertFailsWith<DropArguments> {
        argumentSourceOf(serializer, tooDeeplyLinked).writeTo(sink)
      }

    assertThat(thrown).isSameInstanceAs(sink.failure)
    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
    assertThat(failures).isEmpty()
  }

  @Test
  fun `writeTo() - rethrows the nesting limit that a serializer replaced with a LinkageError, unreported`() {
    val serializer = WrappingSerializer(LinkSerializer) { NoClassDefFoundError("replaced") }
    val sink = TreeArgumentWriter(ArgumentBudget())

    val thrown =
      assertFailsWith<DropArguments> {
        argumentSourceOf(serializer, tooDeeplyLinked).writeTo(sink)
      }

    assertThat(thrown).isSameInstanceAs(sink.failure)
    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
    assertThat(failures).isEmpty()
  }

  @Test
  fun `writeTo() - rethrows the sink's limit when the encoder's nesting limit follows it, unreported`() {
    val serializer = NestsAfterCaughtLimitSerializer(overLimitId)
    val sink = TreeArgumentWriter(ArgumentBudget())

    val thrown =
      assertFailsWith<DropArguments> {
        argumentSourceOf(
            serializer,
            linked(length = KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING),
          )
          .writeTo(sink)
      }

    assertThat(thrown).isSameInstanceAs(sink.failure)
    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(failures).isEmpty()
  }

  // writeTo() after the arguments are read

  @Test
  fun `writeTo() - writes the copy read as a map rather than the entry's current properties`() {
    val entry = MutableDetail("1")
    val source = argumentSourceOf(mutableDetailSerializer, entry)
    source.size
    entry.id = "2"
    val sink = TreeArgumentWriter(ArgumentBudget())

    source.writeTo(sink)

    assertThat(sink.argumentsOrThrow()).isEqualTo(mapOf("id" to "1"))
  }

  @Test
  fun `writeTo() - doesn't encode the entry again after its arguments are read`() {
    val source = argumentSourceOf(mutableDetailSerializer, MutableDetail("1"))
    source.size

    source.writeTo(TreeArgumentWriter(ArgumentBudget()))
    source.writeTo(TreeArgumentWriter(ArgumentBudget()))

    assertThat(mutableDetailSerializer.serialized).hasSize(1)
  }

  @Test
  fun `writeTo() - encodes the entry's current properties when its arguments weren't read`() {
    val entry = MutableDetail("1")
    val source = argumentSourceOf(mutableDetailSerializer, entry)
    entry.id = "2"
    val sink = TreeArgumentWriter(ArgumentBudget())

    source.writeTo(sink)

    assertThat(sink.argumentsOrThrow()).isEqualTo(mapOf("id" to "2"))
  }

  @Test
  fun `reads a copy encoded separately from what writeTo() wrote`() {
    val entry = MutableDetail("1")
    val source = argumentSourceOf(mutableDetailSerializer, entry)
    source.writeTo(TreeArgumentWriter(ArgumentBudget()))
    entry.id = "2"

    val read = source.toMap()

    assertThat(read).isEqualTo(mapOf("id" to "2"))
    assertThat(mutableDetailSerializer.serialized).hasSize(2)
  }

  @Test
  fun `writeTo() - spends the sink's value budget when writing the copy read`() {
    // The entry has 151 values, which fit in its own budget but not in the 100 the sink has left.
    val source = argumentSourceOf(Values.serializer(), Values(List(150) { it }))
    source.size
    val sink = TreeArgumentWriter(budgetWithValuesRemaining(100))

    val thrown = assertFailsWith<DropArguments> { source.writeTo(sink) }

    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.MAX_COUNT)
    assertThat(thrown.warning).isEqualTo(DropArguments.MAX_COUNT_WARNING)
    assertThat(failures).isEmpty()
  }

  @Test
  fun `writeTo() - spends the sink's character budget when writing the copy read`() {
    val source = argumentSourceOf(detailSerializer, Detail("123"))
    source.size
    val sink = TreeArgumentWriter(budgetWithCharactersRemaining(4))

    val thrown = assertFailsWith<DropArguments> { source.writeTo(sink) }

    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(thrown.warning).isEqualTo(DropArguments.MAX_CHARACTER_WARNING)
    assertThat(failures).isEmpty()
  }

  @Test
  fun `writeTo() - encodes again and drops the arguments for the sink's limit when the copy read broke a limit`() {
    val source = argumentSourceOf(detailSerializer, Detail(overLimitId))
    source.size
    val sink = TreeArgumentWriter(ArgumentBudget())

    val thrown = assertFailsWith<DropArguments> { source.writeTo(sink) }

    assertThat(thrown).isSameInstanceAs(sink.failure)
    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(thrown.warning).isEqualTo(DropArguments.MAX_CHARACTER_WARNING)
    assertThat(detailSerializer.serialized).hasSize(2)
    // Only the read reports the limit; the sink's owner reports the one writeTo() broke.
    assertThat(failures).hasSize(1)
  }

  @Test
  fun `writeTo() - encodes the entry again and reports a serializer's exception again when the copy read failed`() {
    val error = IllegalStateException("boom")
    detailSerializer.error = error
    val source = argumentSourceOf(detailSerializer, Detail("1"))
    source.size

    val thrown =
      assertFailsWith<DropArguments> {
        source.writeTo(TreeArgumentWriter(ArgumentBudget()))
      }

    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.ENCODING_FAILED)
    assertThat(thrown.warning).isNull()
    assertThat(detailSerializer.serialized).hasSize(2)
    // The mapper's onFailure logs once per type, so reporting each encoding adds no warnings.
    assertThat(failures).containsExactly(error, error)
  }

  @Test
  fun `writeTo() - drops the arguments for the sink's limit that encoding again breaks when the copy read failed`() {
    // The entry's 151 values fit in the copy's own budget, so the copy failed only once its
    // serializer threw. Encoding again overflows the 100 values the sink has left first.
    val serializer = ThrowsAfterEncodingSerializer(Values.serializer())
    val source = argumentSourceOf(serializer, Values(List(150) { it }))
    source.size
    val sink = TreeArgumentWriter(budgetWithValuesRemaining(100))

    val thrown = assertFailsWith<DropArguments> { source.writeTo(sink) }

    assertThat(thrown).isSameInstanceAs(sink.failure)
    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.MAX_COUNT)
    assertThat(failures).containsExactly(serializer.error)
  }

  @Test
  fun `writeTo() - writes the entry's properties when encoding again succeeds after the copy read failed`() {
    detailSerializer.error = IllegalStateException("boom")
    detailSerializer.failsFor = { detailSerializer.serialized.size == 1 }
    val source = argumentSourceOf(detailSerializer, Detail("1"))
    source.size
    val sink = TreeArgumentWriter(ArgumentBudget())

    source.writeTo(sink)

    assertThat(sink.argumentsOrThrow()).isEqualTo(mapOf("id" to "1"))
  }

  @Test
  fun `reads as only the drop reason of the copy read after writeTo() encodes the entry again`() {
    detailSerializer.error = IllegalStateException("boom")
    detailSerializer.failsFor = { detailSerializer.serialized.size == 1 }
    val source = argumentSourceOf(detailSerializer, Detail("1"))
    source.size
    source.writeTo(TreeArgumentWriter(ArgumentBudget()))

    val read = source.toMap()

    assertThat(read).isEqualTo(encodingFailed)
  }

  /**
   * Reads [source] as a map on a new thread. The returned read's [OtherThreadRead.await] fails
   * rather than hangs if the thread doesn't finish in time.
   */
  private fun readOnAnotherThread(source: Map<String, Any?>): OtherThreadRead {
    val read = OtherThreadRead()
    read.thread =
      thread(name = "other-reader") {
        try {
          read.result = source.toMap()
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
          read.error = t
        }
      }
    return read
  }

  /** A read of the arguments on another thread, whose result [await] returns. */
  private class OtherThreadRead {
    lateinit var thread: Thread
    @Volatile var result: Map<String, Any?>? = null
    @Volatile var error: Throwable? = null

    fun await(): Map<String, Any?>? {
      thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS * 2))
      assertThat(thread.isAlive).isFalse()
      error?.let { throw it }
      return result
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : Any> argumentSourceOf(
    serializer: KSerializer<T>,
    entry: T,
  ): KotlinxArgumentSource =
    KotlinxArgumentSource(entry, serializer as KSerializer<Any>) { failures += it }

  private companion object {
    /** Bounds every wait, so that a deadlock fails the test rather than hanging it. */
    const val TIMEOUT_SECONDS = 5L
  }
}
