package io.sentry.okhttp

import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.TypeCheckHint
import io.sentry.util.network.NetworkRequestData
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
class SentryOkHttpInterceptorStreamingTest {

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
  fun `captures a streamed body once the stream ends`() {
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
  fun `captures a streamed body when the application closes it before the stream ends`() {
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
  fun `records the response of a streamed body the application never reads`() {
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
  fun `captures a streamed error response body`() {
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
      assertTrue(
        captured.length <= io.sentry.SentryReplayOptions.MAX_NETWORK_BODY_SIZE,
        "the capture must stop at the cap, was ${captured.length}",
      )
      assertEquals(whole.substring(0, captured.length), captured)
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
  // responses with a known length keep the existing behaviour
  // ---------------------------------------------------------------------------------------

  @Test
  fun `still captures a response with a known length up front`() {
    setUpSut()
    MockWebServer().use { server ->
      server.enqueue(MockResponse().setBody("response body").setResponseCode(200))

      sut.newCall(requestTo(server.url("/hello").toString())).execute().close()

      assertEquals("response body", capturedBody())
    }
  }

  @Test
  fun `still captures an error response with a known length the application never reads`() {
    setUpSut()
    MockWebServer().use { server ->
      server.enqueue(MockResponse().setBody("failure").setResponseCode(500))

      sut.newCall(requestTo(server.url("/hello").toString())).execute().close()

      assertEquals(500, networkDetails().statusCode)
      assertEquals("failure", capturedBody())
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
  fun `handles a zero length streamed body`() {
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
  fun `keeps the connection usable after a streamed response is closed`() {
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
  ) : Closeable {
    private val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
    private val connections = CopyOnWriteArrayList<Socket>()

    init {
      Thread({ acceptLoop() }, "event-stream-server").apply { isDaemon = true }.start()
    }

    val url: String
      get() = "http://127.0.0.1:${server.localPort}/events"

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

    private fun serve(socket: Socket) {
      try {
        socket.use {
          readRequestHead(it.getInputStream())
          val output = it.getOutputStream()
          output.write(
            ("HTTP/1.1 $statusCode OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nTransfer-Encoding: chunked\r\n\r\n")
              .toByteArray()
          )
          output.flush()
          for (event in events) {
            val bytes = event.toByteArray()
            output.write("${bytes.size.toString(16)}\r\n".toByteArray())
            output.write(bytes)
            output.write("\r\n".toByteArray())
            output.flush()
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
