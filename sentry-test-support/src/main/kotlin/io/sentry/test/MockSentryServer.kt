package io.sentry.test

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.sentry.JsonSerializer
import io.sentry.SentryEnvelope
import io.sentry.SentryOptions
import io.sentry.time.Deadline
import io.sentry.time.JavaMonotonicTicker
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * A stand-in for Sentry's ingestion endpoint that integration tests point the SDK at through [dsn].
 *
 * Envelopes travel through the real HTTP transport, so serialization, compression and the DSN are
 * covered too. It binds a free port so that test tasks running in parallel don't clash, and it
 * works for an app in a child process as well as in the test's own JVM.
 */
class MockSentryServer : Closeable {
  private val serializer = JsonSerializer(SentryOptions.empty())
  private val received = CopyOnWriteArrayList<SentryEnvelope>()
  private val server =
    HttpServer.create(InetSocketAddress(InetAddress.getByName(HOST), 0), 0).apply {
      createContext("/api/") { exchange -> receive(exchange) }
      start()
    }

  val dsn: String = "http://key@$HOST:${server.address.port}/0"

  val envelopes: List<SentryEnvelope>
    get() = received.toList()

  /** Returns the first received envelope matching [predicate], waiting up to [timeoutMillis]. */
  fun awaitEnvelope(
    timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    predicate: (SentryEnvelope) -> Boolean,
  ): SentryEnvelope {
    val deadline =
      Deadline.after(JavaMonotonicTicker.getInstance(), timeoutMillis, TimeUnit.MILLISECONDS)
    while (true) {
      received.firstOrNull(predicate)?.let {
        return it
      }
      if (deadline.hasPassed()) {
        throw AssertionError(
          "No matching envelope within ${timeoutMillis}ms, received ${received.size} envelopes"
        )
      }
      Thread.sleep(POLL_INTERVAL_MILLIS)
    }
  }

  /**
   * Fails if an envelope matching [predicate] has arrived or arrives within [millis].
   *
   * A flush alone can't prove an absence: the SDK may not have produced the envelope yet when the
   * flush runs, for example when a server finishes its transaction after sending the response.
   */
  fun assertNoEnvelope(millis: Long = 2_000, predicate: (SentryEnvelope) -> Boolean) {
    val deadline = Deadline.after(JavaMonotonicTicker.getInstance(), millis, TimeUnit.MILLISECONDS)
    while (true) {
      if (received.any(predicate)) {
        throw AssertionError("Received an envelope that should not have been sent")
      }
      if (deadline.hasPassed()) {
        return
      }
      Thread.sleep(POLL_INTERVAL_MILLIS)
    }
  }

  fun reset() {
    received.clear()
  }

  override fun close() {
    server.stop(0)
  }

  private fun receive(exchange: HttpExchange) {
    try {
      val body =
        if (exchange.requestHeaders.getFirst("Content-Encoding") == "gzip") {
          GZIPInputStream(exchange.requestBody)
        } else {
          exchange.requestBody
        }
      serializer.deserializeEnvelope(body)?.let { received.add(it) }
      exchange.sendResponseHeaders(200, -1)
    } finally {
      exchange.close()
    }
  }

  private companion object {
    // An IP rather than "localhost", which can resolve to IPv4 for the server and IPv6 for the SDK.
    const val HOST = "127.0.0.1"
    const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    const val POLL_INTERVAL_MILLIS = 50L
  }
}
