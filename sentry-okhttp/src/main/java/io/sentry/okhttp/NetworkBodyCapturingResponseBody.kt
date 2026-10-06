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
 * untouched and hands the captured bytes to [onCaptured] as soon as the capture can no longer grow,
 * which is when the stream ends, when the application closes the body, or when the cap is reached.
 *
 * @param delegate the body to capture from.
 * @param maxBytes the maximum number of bytes to retain; capture stops once it is reached.
 * @param onCaptured invoked at most once, synchronously, on the thread that finishes the body. It
 *   is never invoked for a body that is abandoned without being read to the end or closed.
 */
internal class NetworkBodyCapturingResponseBody(
  private val delegate: ResponseBody,
  private val maxBytes: Long,
  private val onCaptured: (ByteArray) -> Unit,
) : ResponseBody() {

  private val captured = Buffer()
  private val reported = AtomicBoolean(false)

  // A ResponseBody must hand out a BufferedSource, so the capturing source is buffered. That cannot
  // re-introduce the blocking this class exists to avoid: BufferedSource.read(sink, byteCount)
  // issues at most one segment-sized read on the source below it and returns with whatever arrived,
  // so a short event is still forwarded on its own. The reads that loop until a byte count is
  // reached — request, require, readByteArray() — are the application's own choice, and the capture
  // never calls them.
  private val capturingSource: BufferedSource by lazy {
    CapturingSource(delegate.source()).buffer()
  }

  override fun contentType(): MediaType? = delegate.contentType()

  override fun contentLength(): Long = delegate.contentLength()

  override fun source(): BufferedSource = capturingSource

  override fun close() {
    try {
      // Report before closing the delegate: closing it notifies listeners, which may serialize the
      // breadcrumb this capture belongs to.
      reportCaptured()
    } finally {
      delegate.close()
    }
  }

  /** Copies the bytes passing through into [captured]. */
  private inner class CapturingSource(source: Source) : ForwardingSource(source) {
    override fun read(sink: Buffer, byteCount: Long): Long {
      val sinkBefore = sink.size
      val read = super.read(sink, byteCount)

      if (read > 0L) {
        val capFull =
          synchronized(captured) {
            val toTake = minOf(maxBytes - captured.size, sink.size - sinkBefore)
            if (toTake > 0L) {
              sink.copyTo(captured, sinkBefore, toTake)
            }
            captured.size >= maxBytes
          }
        if (capFull) {
          // the capture can never grow again, so this is the moment it becomes final
          reportCaptured()
        }
      } else if (read == -1L) {
        // end of stream: nothing more can ever arrive
        reportCaptured()
      }
      return read
    }

    override fun close() {
      try {
        reportCaptured()
      } finally {
        super.close()
      }
    }
  }

  /**
   * The consuming thread writes [captured] from [CapturingSource.read] while [close] may be called
   * by another thread — cancelling a stream from elsewhere is ordinary use — and [Buffer] is not
   * thread-safe, so both accesses are guarded. [reported] keeps the callback to a single
   * invocation.
   */
  private fun reportCaptured() {
    if (reported.compareAndSet(false, true)) {
      onCaptured(synchronized(captured) { captured.clone().readByteArray() })
    }
  }
}
