package io.sentry.okhttp

import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.TypeCheckHint
import io.sentry.util.network.NetworkBody
import io.sentry.util.network.NetworkRequestData
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Response bodies of unknown length: server-sent events, long-poll, chunked endpoints that stay
 * open.
 *
 * These used to hang. Capturing the body means peeking it, and OkHttp implements a peek as "keep
 * reading until the peek size is reached or the stream ends". For these responses neither happens,
 * so the thread that called `execute()` never received the response at all.
 */
class SentryOkHttpInterceptorUnknownContentLengthTest {

  private val scopes = mock<IScopes>()
  private lateinit var options: SentryOptions
  private lateinit var sut: OkHttpClient

  private fun setUpSut(captureBodies: Boolean = true) {
    options =
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        sessionReplay.setNetworkDetailAllowUrls(listOf(".*"))
        sessionReplay.setNetworkCaptureBodies(captureBodies)
      }
    whenever(scopes.options).thenReturn(options)
    sut = OkHttpClient.Builder().addInterceptor(SentryOkHttpInterceptor(scopes)).build()
  }

  private fun requestTo(url: String) = Request.Builder().url(url).build()

  /** The network details the breadcrumb points at; read after the body has been consumed. */
  private fun networkDetails(): NetworkRequestData {
    val hint = argumentCaptor<Hint>()
    verify(scopes).addBreadcrumb(any(), hint.capture())
    return assertNotNull(
      hint.firstValue.getAs(
        TypeCheckHint.SENTRY_REPLAY_NETWORK_DETAILS,
        NetworkRequestData::class.java,
      )
    )
  }

  private fun capturedBody(): String? = networkDetails().response?.body?.body as String?

  private fun capturedBodyValue(): Any? = networkDetails().response?.body?.body

  // ---------------------------------------------------------------------------------------
  // the regression
  // ---------------------------------------------------------------------------------------

  @Test
  fun `returns the response even though the body never ends`() {
    setUpSut()
    EventStreamServer(listOf("data: event-0\n\n"), closeStream = false).use { stream ->
      val executor = Executors.newSingleThreadExecutor { r -> Thread(r).apply { isDaemon = true } }
      val call: Call = sut.newCall(requestTo(stream.url))
      try {
        val response = executor.submit<Response> { call.execute() }.get(5, TimeUnit.SECONDS)
        assertEquals(200, response.code)

        // The stream is still open and nothing has been consumed, so the capture cannot have
        // happened yet. Everything that is already known must still reach the breadcrumb.
        val details = networkDetails()
        assertEquals(200, details.statusCode)
        assertNotNull(details.response, "the response is recorded before the body is consumed")
        assertNull(details.response?.body, "but its body is not known yet")

        response.close()
      } finally {
        executor.shutdownNow()
      }
    }
  }

  @Test
  fun `delivers each small event to the application as it arrives`() {
    setUpSut()
    val events = (0 until 3).map { "data: event-$it\n\n" }
    EventStreamServer(events, closeStream = true).use { stream ->
      sut.newCall(requestTo(stream.url)).execute().use { response ->
        val source = assertNotNull(response.body).source()
        for (event in events) {
          assertEquals(
            event,
            source.readUtf8(event.length.toLong()),
            "the application must see every event",
          )
        }
        assertTrue(source.exhausted(), "the stream ended cleanly")
      }
    }
  }

  // ---------------------------------------------------------------------------------------
  // what ends up captured
  // ---------------------------------------------------------------------------------------

  @Test
  fun `captures a body of unknown length once the stream ends`() {
    setUpSut()
    val events = listOf("data: event-0\n\n", "data: event-1\n\n")
    EventStreamServer(events, closeStream = true).use { stream ->
      sut.newCall(requestTo(stream.url)).execute().use { it.body?.string() }
      val details = networkDetails()
      assertEquals(200, details.statusCode)
      assertEquals(events.joinToString(""), capturedBody())
      assertEquals(events.joinToString("").length.toLong(), details.responseBodySize)
    }
  }

  @Test
  fun `captures a body of unknown length when the application closes it before the stream ends`() {
    setUpSut()
    EventStreamServer(listOf("data: event-0\n\n", "data: event-1\n\n"), closeStream = false).use {
      stream ->
      sut.newCall(requestTo(stream.url)).execute().use { response ->
        assertEquals("data: event-0\n\n", assertNotNull(response.body).source().readUtf8(15))
      }

      assertEquals("data: event-0\n\n", capturedBody())
    }
  }

  @Test
  fun `records the response of a body of unknown length the application never reads`() {
    setUpSut()
    EventStreamServer(listOf("data: event-0\n\n"), closeStream = true).use { stream ->
      sut.newCall(requestTo(stream.url)).execute().close()

      val details = networkDetails()
      assertEquals(200, details.statusCode)
      assertNull(
        capturedBody(),
        "an unread stream is never peeked, so there is nothing to report; the response is still recorded",
      )
    }
  }

  @Test
  fun `captures an error response body of unknown length`() {
    setUpSut()
    EventStreamServer(listOf("data: boom\n\n"), closeStream = true, statusCode = 500).use { stream
      ->
      sut.newCall(requestTo(stream.url)).execute().use { it.body?.string() }

      val details = networkDetails()
      assertEquals(500, details.statusCode)
      assertEquals("data: boom\n\n", capturedBody())
    }
  }

  @Test
  fun `caps the captured body without truncating the body the application reads`() {
    setUpSut()
    val events = (0 until 200).map { "data: " + "x".repeat(1000) + "\n\n" }
    val whole = events.joinToString("")
    EventStreamServer(events, closeStream = true).use { stream ->
      val received = sut.newCall(requestTo(stream.url)).execute().use { it.body?.string() }

      assertEquals(whole.length, received?.length, "the application must receive the whole stream")
      val captured = assertNotNull(capturedBody())
      assertEquals(
        io.sentry.SentryReplayOptions.MAX_NETWORK_BODY_SIZE,
        captured.length,
        "the capture must stop at the cap",
      )
      assertEquals(whole.substring(0, captured.length), captured)
      assertEquals(
        listOf(NetworkBody.NetworkBodyWarning.TEXT_TRUNCATED),
        networkDetails().response?.body?.warnings,
        "a capped capture must be reported as truncated, not as a complete body",
      )
    }
  }

  @Test
  fun `does not capture bodies when network body capture is turned off`() {
    setUpSut(captureBodies = false)
    EventStreamServer(listOf("data: event-0\n\n"), closeStream = true).use { stream ->
      sut.newCall(requestTo(stream.url)).execute().use { it.body?.string() }

      assertEquals(200, networkDetails().statusCode)
      assertNull(capturedBody())
    }
  }

  // ---------------------------------------------------------------------------------------
  // how the body is actually consumed
  // ---------------------------------------------------------------------------------------

  @Test
  fun `captures a body consumed from the callback of an asynchronous call`() {
    // enqueue() hands the response to a dispatcher thread, so the capture is completed by a thread
    // other than the one that started the call. That is the publication NetworkRequestData guards.
    setUpSut()
    val events = (0 until 3).map { "data: event-$it\n\n" }
    EventStreamServer(events, closeStream = true).use { stream ->
      val body = AtomicReference<String>()
      val thread = AtomicReference<String>()
      val done = CountDownLatch(1)

      sut
        .newCall(requestTo(stream.url))
        .enqueue(
          object : Callback {
            override fun onFailure(call: Call, e: IOException) = done.countDown()

            override fun onResponse(call: Call, response: Response) {
              response.use { body.set(it.body?.string()) }
              thread.set(Thread.currentThread().name)
              done.countDown()
            }
          }
        )

      assertTrue(done.await(10, TimeUnit.SECONDS), "the callback must be reached")
      assertEquals(events.joinToString(""), body.get())
      assertNotEquals(
        Thread.currentThread().name,
        thread.get(),
        "the body is consumed off the calling thread",
      )
      assertEquals(events.joinToString(""), capturedBody())
      assertEquals(200, networkDetails().statusCode)
    }
  }

  @Test
  fun `captures what arrived when a stream is cancelled while a reader is parked on it`() {
    // How an SSE client consumes a stream: line by line on its own thread, until the call is
    // cancelled. Nothing closes the body afterwards, so the broken read is what completes the
    // capture, with the events that did arrive.
    setUpSut()
    val events = (0 until 3).map { "data: event-$it\n\n" }
    EventStreamServer(events, closeStream = false).use { stream ->
      val call = sut.newCall(requestTo(stream.url))
      val response = call.execute()
      val readThree = CountDownLatch(3)
      val failed = CountDownLatch(1)
      val reader = Thread {
        @Suppress("SwallowedException") // cancelling the call is how this read is meant to end
        try {
          val source = response.body!!.source()
          while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.startsWith("data:")) readThree.countDown()
          }
        } catch (e: IOException) {
          failed.countDown()
        }
      }
      reader.isDaemon = true
      reader.start()

      assertTrue(readThree.await(10, TimeUnit.SECONDS), "the reader must receive the events")
      call.cancel()

      assertTrue(failed.await(10, TimeUnit.SECONDS), "the parked read must end with an IOException")
      assertEquals(200, networkDetails().statusCode)
      assertEquals(events.joinToString(""), capturedBody())
    }
  }

  @Test
  fun `captures what arrived when the server drops the connection mid stream`() {
    setUpSut()
    val events = (0 until 3).map { "data: event-$it\n\n" }
    EventStreamServer(events, closeStream = false, dropAfterEvents = true).use { stream ->
      val response = sut.newCall(requestTo(stream.url)).execute()

      assertFailsWith<IOException> { response.body?.string() }

      assertEquals(200, networkDetails().statusCode)
      assertEquals(events.joinToString(""), capturedBody())
    }
  }

  @Test
  fun `captures a body of unknown length over HTTP2`() {
    // HTTP/2 has no chunked encoding and ends a body with an empty DATA frame, so it reaches the
    // wrapper by a different route than the HTTP/1.1 tests above.
    setUpSut()
    val payload = "data: over-h2\n\n"
    MockWebServer().use { server ->
      server.protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
      server.enqueue(
        MockResponse()
          .setBody(payload)
          .removeHeader("Content-Length")
          .setHeader("Content-Type", "text/event-stream")
      )
      sut = sut.newBuilder().protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)).build()

      val response = sut.newCall(requestTo(server.url("/events").toString())).execute()
      assertEquals(payload, response.use { it.body?.string() })

      assertEquals(Protocol.H2_PRIOR_KNOWLEDGE, response.protocol)
      assertEquals(-1L, response.body?.contentLength(), "no known length on the h2 body")
      assertEquals(payload, capturedBody())
      assertEquals(200, networkDetails().statusCode)
    }
  }

  // ---------------------------------------------------------------------------------------
  // responses with a known length
  // ---------------------------------------------------------------------------------------

  @Test
  fun `captures a gzipped body, which okhttp hands over without a known length`() {
    // OkHttp asks for gzip itself and decompresses transparently, dropping Content-Length on the
    // way, so an application interceptor sees -1 for an ordinary gzipped JSON response.
    setUpSut()
    val json = """{"hello":"world"}"""
    val gzipped = Buffer()
    GzipSink(gzipped).buffer().use { it.writeUtf8(json) }
    MockWebServer().use { server ->
      server.enqueue(
        MockResponse()
          .setBody(gzipped)
          .setHeader("Content-Encoding", "gzip")
          .setHeader("Content-Type", "application/json")
      )

      val response = sut.newCall(requestTo(server.url("/json").toString())).execute()
      assertEquals(json, response.use { it.body?.string() })

      assertEquals(
        -1L,
        response.body?.contentLength(),
        "okhttp reports no length for a gzipped body",
      )
      assertEquals(mapOf("hello" to "world"), capturedBodyValue())
      assertEquals(200, networkDetails().statusCode)
    }
  }

  @Test
  fun `captures a body with a known length as the application reads it`() {
    setUpSut()
    MockWebServer().use { server ->
      server.enqueue(MockResponse().setBody("response body").setResponseCode(200))

      sut.newCall(requestTo(server.url("/hello").toString())).execute().use { it.body?.string() }

      assertEquals("response body", capturedBody())
    }
  }

  @Test
  fun `records an error response the application never reads, without its body`() {
    // Nothing is read on the capture's own account, not even a body that would end on its own, so
    // the interceptor costs a consumer no more than the bytes it asked for itself.
    setUpSut()
    MockWebServer().use { server ->
      server.enqueue(MockResponse().setBody("failure").setResponseCode(500))

      sut.newCall(requestTo(server.url("/hello").toString())).execute().close()

      assertEquals(500, networkDetails().statusCode)
      assertNull(capturedBody())
    }
  }

  @Test
  fun `handles a zero length body`() {
    setUpSut()
    MockWebServer().use { server ->
      server.enqueue(MockResponse().setResponseCode(204))

      sut.newCall(requestTo(server.url("/hello").toString())).execute().use { response ->
        assertEquals(204, response.code)
        assertEquals(0, assertNotNull(response.body).contentLength())
      }

      assertEquals(204, networkDetails().statusCode)
      assertNull(capturedBody())
    }
  }

  @Test
  fun `handles a zero length body of unknown length`() {
    setUpSut()
    EventStreamServer(emptyList(), closeStream = true).use { stream ->
      sut.newCall(requestTo(stream.url)).execute().use { response ->
        assertEquals("", response.body?.string())
      }

      assertEquals(200, networkDetails().statusCode)
      assertNull(capturedBody())
    }
  }

  @Test
  fun `keeps the connection usable after a response of unknown length is closed`() {
    setUpSut()
    EventStreamServer(listOf("data: event-0\n\n"), closeStream = true).use { stream ->
      // a second call on the same client proves the connection was released properly
      repeat(3) {
        sut.newCall(requestTo(stream.url)).execute().use { assertEquals(200, it.code) }
      }
    }
  }

  /**
   * Minimal HTTP/1.1 origin that streams one chunk per event and can leave the stream open, so the
   * response body never ends.
   */
  private class EventStreamServer(
    private val events: List<String>,
    private val closeStream: Boolean,
    private val statusCode: Int = 200,
    private val dropAfterEvents: Boolean = false,
  ) : Closeable {
    private val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
    private val connections = CopyOnWriteArrayList<Socket>()

    init {
      Thread({ acceptLoop() }, "event-stream-server").apply { isDaemon = true }.start()
    }

    val url: String
      get() = "http://127.0.0.1:${server.localPort}/events"

    @Suppress("SwallowedException") // the server socket is closed when the test finishes
    private fun acceptLoop() {
      while (!server.isClosed) {
        val socket =
          try {
            server.accept()
          } catch (e: IOException) {
            return
          }
        connections.add(socket)
        Thread({ serve(socket) }, "event-stream-connection").apply { isDaemon = true }.start()
      }
    }

    @Suppress("SwallowedException") // a client that hangs up mid-stream is normal here
    private fun serve(socket: Socket) {
      try {
        socket.use {
          readRequestHead(it.getInputStream())
          val output = it.getOutputStream()
          val headers =
            "HTTP/1.1 $statusCode OK\r\n" +
              "Content-Type: text/event-stream\r\n" +
              "Cache-Control: no-cache\r\n" +
              "Transfer-Encoding: chunked\r\n\r\n"
          output.write(headers.toByteArray())
          output.flush()
          for (event in events) {
            val bytes = event.toByteArray()
            output.write("${bytes.size.toString(16)}\r\n".toByteArray())
            output.write(bytes)
            output.write("\r\n".toByteArray())
            output.flush()
          }
          if (dropAfterEvents) {
            // hang up without the terminating chunk, the way a mobile connection dies
            return
          }
          if (closeStream) {
            output.write("0\r\n\r\n".toByteArray())
            output.flush()
          } else {
            // hold the connection open: the body stays open ended
            Thread.sleep(30_000)
          }
        }
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
      } catch (e: IOException) {
        // the client went away
      }
    }

    private fun readRequestHead(input: java.io.InputStream) {
      val head = ByteArrayOutputStream()
      while (true) {
        val byte = input.read()
        if (byte == -1) {
          return
        }
        head.write(byte)
        if (head.size() >= 4 && head.toString(Charsets.ISO_8859_1).endsWith("\r\n\r\n")) {
          return
        }
      }
    }

    override fun close() {
      runCatching { server.close() }
      connections.forEach { runCatching { it.close() } }
    }
  }
}
