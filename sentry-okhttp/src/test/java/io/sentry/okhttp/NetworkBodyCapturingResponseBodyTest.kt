package io.sentry.okhttp

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer

class NetworkBodyCapturingResponseBodyTest {

  /** Emits one chunk per read, then EOF. Records how many reads happened. */
  private class ChunkedSource(private val chunks: List<ByteArray>) : Source {
    var reads = 0
      private set

    private var index = 0
    private var closed = false

    override fun read(sink: Buffer, byteCount: Long): Long {
      reads++
      if (index >= chunks.size) return -1L
      val chunk = chunks[index++]
      sink.write(chunk)
      return chunk.size.toLong()
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() {
      closed = true
    }

    val isClosed: Boolean
      get() = closed
  }

  private class FailingSource(private val bytesBeforeFailure: Int) : Source {
    var isClosed = false
      private set

    private var written = 0

    override fun read(sink: Buffer, byteCount: Long): Long {
      if (written >= bytesBeforeFailure) throw IOException("connection reset")
      sink.write(ByteArray(bytesBeforeFailure) { 'x'.code.toByte() })
      written += bytesBeforeFailure
      return bytesBeforeFailure.toLong()
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() {
      isClosed = true
    }
  }

  /** Emits one chunk, then parks until it is closed, like a stream that has gone quiet. */
  private class ParkingSource(private val chunk: ByteArray) : Source {
    val emitted = CountDownLatch(1)
    private val closed = CountDownLatch(1)
    private var sent = false

    override fun read(sink: Buffer, byteCount: Long): Long {
      if (!sent) {
        sent = true
        sink.write(chunk)
        emitted.countDown()
        return chunk.size.toLong()
      }
      closed.await(10, TimeUnit.SECONDS)
      return -1L
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() {
      closed.countDown()
    }
  }

  private fun bodyOf(
    source: Source,
    type: String? = "text/plain",
    length: Long = -1L,
  ): ResponseBody =
    object : ResponseBody() {
      override fun contentType(): MediaType? = type?.toMediaType()

      override fun contentLength(): Long = length

      override fun source(): BufferedSource = source.buffer()
    }

  private fun capture(
    source: Source,
    maxBytes: Long,
    type: String? = "text/plain",
    length: Long = -1L,
  ): Pair<NetworkBodyCapturingResponseBody, MutableList<ByteArray?>> {
    val captured = mutableListOf<ByteArray?>()
    val wrapper =
      NetworkBodyCapturingResponseBody(bodyOf(source, type, length), maxBytes) {
        captured.add(it)
      }
    return wrapper to captured
  }

  @Test
  fun `does not read anything before the application does`() {
    val source = ChunkedSource(listOf("hello".toByteArray()))
    val (wrapper, captured) = capture(source, 1024)

    assertEquals(0, source.reads, "the wrapper must not read the body up front")
    assertTrue(captured.isEmpty(), "nothing can be captured before the application reads")
    assertEquals("text/plain", wrapper.contentType()?.toString(), "content type is delegated")
  }

  @Test
  fun `forwards every byte to the application unchanged`() {
    val payload = "data: event-0\n\ndata: event-1\n\n".toByteArray()
    val (wrapper, _) = capture(ChunkedSource(listOf(payload)), 1024)

    assertContentEquals(payload, wrapper.source().readByteArray())
  }

  @Test
  fun `captures the whole body when it is consumed and ends`() {
    val (wrapper, captured) =
      capture(
        ChunkedSource(listOf("hello ".toByteArray(), "world".toByteArray())),
        1024,
      )

    wrapper.source().readByteArray()

    assertEquals(1, captured.size, "the capture must be reported exactly once")
    assertEquals("hello world", captured.single()?.decodeToString())
  }

  @Test
  fun `passes small events through as they arrive and captures them on close`() {
    val events = (0 until 3).map { "data: event-$it\n\n".toByteArray() }
    val source = ChunkedSource(events)
    val (wrapper, captured) = capture(source, 1024)

    val application = wrapper.source()
    for (expected in events) {
      assertContentEquals(expected, application.readByteArray(expected.size.toLong()))
    }

    assertTrue(captured.isEmpty(), "an open stream has nothing final to report yet")

    wrapper.close()

    assertEquals(1, captured.size)
    assertEquals(
      "data: event-0\n\ndata: event-1\n\ndata: event-2\n\n",
      captured.single()?.decodeToString(),
    )
  }

  @Test
  fun `captures what was read when the body is closed early`() {
    val (wrapper, captured) =
      capture(ChunkedSource(listOf("first ".toByteArray(), "second".toByteArray())), 1024)

    val application = wrapper.source()
    assertEquals("first ", application.readUtf8(6))

    wrapper.close()

    assertEquals(1, captured.size)
    assertEquals("first ", captured.single()?.decodeToString())
  }

  @Test
  fun `captures a body of known length that the application never read`() {
    val payload = "failure".toByteArray()
    val source = ChunkedSource(listOf(payload))
    val (wrapper, captured) = capture(source, 1024, length = payload.size.toLong())

    wrapper.close()

    assertContentEquals(payload, captured.single(), "a bounded body is taken while closing")
    assertTrue(source.isClosed)
  }

  @Test
  fun `does not read a body of unknown length that the application never read`() {
    val source = ChunkedSource(listOf("data: event-0\n\n".toByteArray()))
    val (wrapper, captured) = capture(source, 1024)

    wrapper.close()

    assertEquals(0, source.reads, "a body that may never end must not be read while closing")
    assertTrue(captured.single()!!.isEmpty())
  }

  @Test
  fun `reports an empty capture for an empty body`() {
    val (wrapper, captured) = capture(ChunkedSource(emptyList()), 1024)

    wrapper.source().readByteArray()
    wrapper.close()

    assertEquals(1, captured.size)
    assertEquals(0, captured.single()?.size)
  }

  @Test
  fun `never captures more than the cap but still delivers the whole body`() {
    val payload = "0123456789abcdefghij".toByteArray()
    val (wrapper, captured) = capture(ChunkedSource(listOf(payload)), 8)

    assertContentEquals(
      payload,
      wrapper.source().readByteArray(),
      "the application is not truncated",
    )

    assertEquals(1, captured.size, "reaching the cap is final, so it is reported once")
    assertEquals("01234567", captured.single()?.decodeToString())
  }

  @Test
  fun `captures every byte when the application reads less than a chunk at a time`() {
    // The capture copies out of the application's sink at an offset, because a BufferedSource keeps
    // what the application did not take yet in that same sink.
    val chunks = (0 until 5).map { i -> "chunk-$i--".toByteArray() }
    val whole = chunks.joinToString("") { it.decodeToString() }
    val (wrapper, captured) = capture(ChunkedSource(chunks), 1024)

    val source = wrapper.source()
    val read = StringBuilder()
    while (!source.exhausted()) {
      read.append(source.readUtf8(minOf(3L, source.buffer.size.coerceAtLeast(1L))))
    }

    assertEquals(whole, read.toString(), "the application receives the whole body")
    assertEquals(whole, captured.single()?.decodeToString(), "and the capture holds the same bytes")
  }

  @Test
  fun `a failing capture callback does not reach the application`() {
    val source = ChunkedSource(listOf("payload".toByteArray()))
    val body = bodyOf(source)
    val wrapper =
      NetworkBodyCapturingResponseBody(body, 1024) { throw IllegalStateException("parse failed") }

    // The interceptor guards its own callback; the wrapper must not swallow the failure silently
    // while leaving the delegate open.
    assertFailsWith<IllegalStateException> { wrapper.close() }
    assertTrue(source.isClosed, "the delegate is closed even though the callback threw")
  }

  @Test
  fun `reports once when the body is closed while another thread is reading it`() {
    // Cancelling a stream from elsewhere closes the body from a thread other than the consumer, so
    // the capture is read and written at the same time.
    val chunk = "data: event-0\n\n".toByteArray()
    val source = ParkingSource(chunk)
    val (wrapper, captured) = capture(source, 1024)
    val readDone = CountDownLatch(1)
    val reader = Thread {
      wrapper.source().readByteArray()
      readDone.countDown()
    }
    reader.isDaemon = true
    reader.start()

    assertTrue(source.emitted.await(10, TimeUnit.SECONDS), "the reader must have taken the chunk")
    wrapper.close()

    assertTrue(
      readDone.await(10, TimeUnit.SECONDS),
      "the reader must finish once the body is closed",
    )
    assertEquals(
      1,
      captured.size,
      "the capture is reported once, by whichever thread got there first",
    )
    assertContentEquals(chunk, captured.single())
  }

  @Test
  fun `reports only once when the body ends and is then closed`() {
    val (wrapper, captured) = capture(ChunkedSource(listOf("done".toByteArray())), 1024)

    wrapper.source().readByteArray()
    wrapper.close()
    wrapper.source().close()

    assertEquals(1, captured.size, "the capture is reported exactly once")
  }

  @Test
  fun `delegates content type and content length`() {
    val type = "text/event-stream".toMediaType()
    val delegate =
      object : ResponseBody() {
        override fun contentType(): MediaType? = type

        override fun contentLength(): Long = -1L

        override fun source(): BufferedSource = ChunkedSource(emptyList()).buffer()
      }

    val wrapper = NetworkBodyCapturingResponseBody(delegate, 16) {}

    assertEquals(type, wrapper.contentType())
    assertEquals(-1L, wrapper.contentLength())
  }

  @Test
  fun `propagates read failures to the application`() {
    val source = FailingSource(bytesBeforeFailure = 4)
    val captured = mutableListOf<ByteArray?>()
    val wrapper = NetworkBodyCapturingResponseBody(bodyOf(source), 1024) { captured.add(it) }

    assertFailsWith<IOException> { wrapper.source().readByteArray() }
    assertEquals(
      "xxxx",
      captured.single()?.decodeToString(),
      "what arrived before the failure is reported, since nothing more can arrive",
    )

    wrapper.close()
    assertEquals(1, captured.size, "closing afterwards does not report a second time")
    assertTrue(source.isClosed, "the connection must still be releasable after a failure")
  }

  @Test
  fun `closes the delegate body`() {
    val source = ChunkedSource(listOf("x".toByteArray()))
    val (wrapper, _) = capture(source, 1024)

    assertFalse(source.isClosed)
    wrapper.close()
    assertTrue(source.isClosed, "the application must still be able to release the connection")
  }
}
