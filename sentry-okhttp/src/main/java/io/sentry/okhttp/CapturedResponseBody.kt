package io.sentry.okhttp

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer

/**
 * A [ResponseBody] that copies the bytes the application reads into a capped buffer.
 *
 * @param delegate the body to capture from.
 * @param maxBytes the maximum number of bytes to retain; capture stops once it is reached.
 * @param onCaptured invoked at most once, as soon as the capture can no longer grow: the cap is
 *   reached, the stream ends, a read fails, or the body is closed. Runs synchronously on the thread
 *   that read or closed the body, so it must not block. A body abandoned without any of those never
 *   reaches it.
 */
internal class CapturedResponseBody(
  private val delegate: ResponseBody,
  private val maxBytes: Long,
  private val onCaptured: (ByteArray) -> Unit,
) : ResponseBody() {

  private val captured = Buffer()
  private val capturedOut = captured.outputStream()

  private val reported = AtomicBoolean(false)

  private val capturingSource: BufferedSource by lazy {
    CapturingSource(delegate.source()).buffer()
  }

  override fun contentType(): MediaType? = delegate.contentType()

  override fun contentLength(): Long = delegate.contentLength()

  override fun source(): BufferedSource = capturingSource

  override fun close() {
    // Closing the delegate notifies listeners, which may serialize the breadcrumb this capture
    // belongs to, so report first.
    delegate.use { reportCaptured() }
  }

  private inner class CapturingSource(source: Source) : ForwardingSource(source) {
    override fun read(sink: Buffer, byteCount: Long): Long {
      val sinkBefore = sink.size
      val read =
        try {
          super.read(sink, byteCount)
        } catch (e: IOException) {
          // What arrived is the evidence for this very failure, and a caller handling it is not
          // obliged to close the body.
          reportCaptured()
          throw e
        }

      if (read > 0L) {
        val capFull =
          synchronized(captured) {
            // the application may not have taken everything in the sink yet, hence the offset
            val toTake = minOf(maxBytes - captured.size, sink.size - sinkBefore)
            if (toTake > 0L) {
              sink.copyTo(capturedOut, sinkBefore, toTake)
            }
            captured.size >= maxBytes
          }
        if (capFull) {
          reportCaptured()
        }
      } else if (read == -1L) {
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

  private fun reportCaptured() {
    if (reported.compareAndSet(false, true)) {
      onCaptured(synchronized(captured) { captured.clone().readByteArray() })
    }
  }
}
