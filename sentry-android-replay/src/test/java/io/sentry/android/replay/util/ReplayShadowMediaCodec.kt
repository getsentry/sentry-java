package io.sentry.android.replay.util

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaCodec
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean

@Implements(MediaCodec::class)
class ReplayShadowMediaCodec : ShadowMediaCodec() {
    companion object {
        var frameRate = 1
        var framesToEncode = 5
    }

    private val encoded = AtomicBoolean(false)

    @Implementation
    fun start() {
        super.native_start()
    }

    @Implementation
    fun signalEndOfInputStream() {
        encodeFrame(framesToEncode, frameRate, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
    }

    @Implementation
    fun getOutputBuffers(): Array<ByteBuffer> = super.getBuffers(false)

    @Implementation
    fun dequeueOutputBuffer(
        info: BufferInfo,
        timeoutUs: Long,
    ): Int {
        val encoderStatus = super.native_dequeueOutputBuffer(info, timeoutUs)
        super.validateOutputByteBuffer(getOutputBuffers(), encoderStatus, info)
        if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER && !encoded.getAndSet(true)) {
            // MediaMuxer is initialized now, so we can start encoding frames
            repeat(framesToEncode) { encodeFrame(it, frameRate) }
        }
        return encoderStatus
    }

    private fun encodeFrame(
        index: Int,
        frameRate: Int,
        size: Int = 10,
        flags: Int = 0,
    ) {
        val presentationTime = MICROSECONDS.convert(index * (1000L / frameRate), MILLISECONDS)
        super.native_dequeueInputBuffer(0)
        super.native_queueInputBuffer(
            index,
            index * size,
            size,
            presentationTime,
            flags,
        )
    }
}
