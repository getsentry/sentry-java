package io.sentry.android.replay.util

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaCodec

@Implements(MediaCodec::class)
class ReplayShadowMediaCodec : ShadowMediaCodec() {
  companion object {
    var frameRate = 1
    var framesToEncode = 5
    var throwOnStart = false

    /** Simulates an encoder that never emits [MediaCodec.BUFFER_FLAG_END_OF_STREAM]. */
    var neverSignalEos = false

    /**
     * When set, [dequeueOutputBuffer] awaits this latch, simulating a native call that never
     * returns. [blockedOnDequeue] is counted down right before, so tests can wait until the codec
     * is actually stuck.
     */
    var blockOnDequeue: CountDownLatch? = null

    var blockedOnDequeue = CountDownLatch(1)
  }

  private val encoded = AtomicBoolean(false)

  @Implementation
  fun start() {
    if (throwOnStart) {
      throw IllegalStateException("Simulated codec start failure")
    }
    super.native_start()
  }

  @Implementation
  fun signalEndOfInputStream() {
    if (neverSignalEos) {
      return
    }
    encodeFrame(framesToEncode, frameRate, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
  }

  @Implementation fun getOutputBuffers(): Array<ByteBuffer> = super.getBuffers(false)

  @Implementation
  fun dequeueOutputBuffer(info: BufferInfo, timeoutUs: Long): Int {
    blockOnDequeue?.let {
      blockedOnDequeue.countDown()
      it.await()
    }
    val encoderStatus = super.native_dequeueOutputBuffer(info, timeoutUs)
    super.validateOutputByteBuffer(getOutputBuffers(), encoderStatus, info)
    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER && !encoded.getAndSet(true)) {
      // MediaMuxer is initialized now, so we can start encoding frames
      repeat(framesToEncode) { encodeFrame(it, frameRate) }
    }
    return encoderStatus
  }

  private fun encodeFrame(index: Int, frameRate: Int, size: Int = 10, flags: Int = 0) {
    val presentationTime = MICROSECONDS.convert(index * (1000L / frameRate), MILLISECONDS)
    super.native_dequeueInputBuffer(0)
    super.native_queueInputBuffer(index, index * size, size, presentationTime, flags)
  }
}
