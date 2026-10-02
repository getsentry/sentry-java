package io.sentry.okhttp

import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer

/**
 * A [ResponseBody] that copies the bytes the application consumes into a capped buffer.
 *
 * Capturing a body by peeking at it up front does not work for every response. OkHttp implements
 * [okhttp3.Response.peekBody] as `request(byteCount)`, which keeps reading until the requested
 * number of bytes is buffered or the stream ends. A response of unknown length may never end — a
 * server-sent-events stream, a long-poll, a chunked endpoint that stays open — so the peek never
 * returns and the thread that called `execute()` never gets the response at all.
 *
 * This body instead captures what is actually consumed. It forwards every byte to the application
 * untouched and hands the captured bytes to [onCaptured] as soon as no more bytes can arrive, which
 * is when the stream ends, when the application closes the body, or when the cap is reached.
 *
 * @param delegate the body to capture from.
 * @param maxBytes the maximum number of bytes to retain; capture stops once it is reached.
 * @param onCaptured invoked exactly once with the captured bytes.
 */
internal class NetworkBodyCapturingResponseBody(
  private val delegate: ResponseBody,
  private val maxBytes: Long,
  private val onCaptured: (ByteArray) -> Unit,
) : ResponseBody() {

  private val captured = Buffer()
  private val reported = AtomicBoolean(false)
  private val capturingSource: BufferedSource by lazy {
    CapturingSource(delegate.source()).buffer()
  }

  override fun contentType(): MediaType? = delegate.contentType()

  override fun contentLength(): Long = delegate.contentLength()

  override fun source(): BufferedSource = capturingSource

  override fun close() {
    // Report before closing the delegate: closing it notifies listeners, which may serialize the
    // breadcrumb this capture belongs to.
    reportCaptured()
    delegate.close()
  }

  private inner class CapturingSource(source: Source) : ForwardingSource(source) {
    override fun read(sink: Buffer, byteCount: Long): Long {
      val sinkBefore = sink.size
      val read = super.read(sink, byteCount)

      if (read > 0L) {
        val reachedCap =
          synchronized(captured) {
            val room = maxBytes - captured.size
            val toTake = minOf(room, sink.size - sinkBefore)
            if (toTake > 0L) {
              sink.copyTo(captured, sinkBefore, toTake)
            }
            captured.size >= maxBytes
          }
        if (reachedCap) {
          reportCaptured()
        }
      } else if (read == -1L) {
        // End of stream: nothing more will ever be captured.
        reportCaptured()
      }
      return read
    }

    override fun close() {
      super.close()
      reportCaptured()
    }
  }

  private fun reportCaptured() {
    if (reported.compareAndSet(false, true)) {
      val bytes = synchronized(captured) { captured.clone().readByteArray() }
      onCaptured(bytes)
    }
  }
}
