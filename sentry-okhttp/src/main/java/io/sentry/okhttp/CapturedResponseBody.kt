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
 *   reaches it. `isComplete` is false when the bytes are only a prefix of the body.
 */
internal class CapturedResponseBody(
  private val delegate: ResponseBody,
  private val maxBytes: Long,
  private val onCaptured: (bytes: ByteArray, isComplete: Boolean) -> Unit,
) : ResponseBody() {

  private val captured = Buffer()
  private val capturedOut = captured.outputStream()

  private val reported = AtomicBoolean(false)
  @Volatile private var bytesRead = 0L
  @Volatile private var endOfStream = false

  private val capturingSourceLazy = lazy { CapturingSource(delegate.source()).buffer() }
  private val capturingSource: BufferedSource by capturingSourceLazy

  override fun contentType(): MediaType? = delegate.contentType()

  override fun contentLength(): Long = delegate.contentLength()

  override fun source(): BufferedSource = capturingSource

  override fun close() {
    // Closing the delegate notifies listeners, which may serialize the breadcrumb this capture
    // belongs to, so report first.
    delegate.use {
      try {
        reportCaptured()
      } finally {
        // the application may still hold the source, and must not read buffered bytes from it
        if (capturingSourceLazy.isInitialized()) {
          capturingSource.close()
        }
      }
    }
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
        bytesRead += read
        if (!reported.get()) {
          val capFull =
            synchronized(captured) {
              // checked again under the lock, so nothing is copied into a drained capture
              if (reported.get()) {
                return@synchronized false
              }
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
        }
      } else if (read == -1L) {
        endOfStream = true
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
      val length = delegate.contentLength()
      val isComplete = endOfStream || (length >= 0L && bytesRead >= length)
      onCaptured(synchronized(captured) { captured.readByteArray() }, isComplete)
    }
  }
}
