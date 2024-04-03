/**
 * Adapted from https://github.com/fzyzcjy/flutter_screen_recorder/blob/dce41cec25c66baf42c6bac4198e95874ce3eb9d/packages/fast_screen_recorder/android/src/main/kotlin/com/cjy/fast_screen_recorder/SimpleFrameMuxer.kt
 *
 * Copyright (c) 2021 fzyzcjy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * In addition to the standard MIT license, this library requires the following:
 * The recorder itself only saves data on user's phone locally, thus it does not have any privacy problem.
 * However, if you are going to get the records out of the local storage (e.g. upload the records to your server),
 * please explicitly ask the user for permission, and promise to only use the records to debug your app.
 * This is a part of the license of this library.
 */
package io.sentry.android.replay.video

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryOptions
import io.sentry.android.replay.ScreenshotRecorderConfig
import java.io.File
import java.nio.ByteBuffer

private const val TIMEOUT_USEC = 100_000L

@TargetApi(26)
internal class SimpleVideoEncoder(
    val options: SentryOptions,
    val muxerConfig: MuxerConfig,
    val onClose: (() -> Unit)? = null
) {
    private val mediaFormat: MediaFormat = run {
        val format = MediaFormat.createVideoFormat(
            muxerConfig.mimeType,
            muxerConfig.recorderConfig.recordingWidth,
            muxerConfig.recorderConfig.recordingHeight
        )

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, muxerConfig.bitrate)
        format.setFloat(MediaFormat.KEY_FRAME_RATE, muxerConfig.frameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)

        format
    }

    internal val mediaCodec: MediaCodec = run {
//    val codecs = MediaCodecList(REGULAR_CODECS)
//    val codecName = codecs.findEncoderForFormat(mediaFormat)
//    val codec = MediaCodec.createByCodecName(codecName)
        val codec = MediaCodec.createEncoderByType(muxerConfig.mimeType)

        codec
    }

    private val bufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()
    private val frameMuxer = muxerConfig.frameMuxer
    val duration get() = frameMuxer.getVideoTime()

    private var surface: Surface? = null

    fun start() {
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = mediaCodec.createInputSurface()
        mediaCodec.start()
        drainCodec(false)
    }

    fun encode(image: Bitmap) {
        // NOTE do not use `lockCanvas` like what is done in bitmap2video
        // This is because https://developer.android.com/reference/android/media/MediaCodec#createInputSurface()
        // says that, "Surface.lockCanvas(android.graphics.Rect) may fail or produce unexpected results."
        val canvas = surface?.lockHardwareCanvas()
        canvas?.drawBitmap(image, 0f, 0f, null)
        surface?.unlockCanvasAndPost(canvas)
        drainCodec(false)
    }

    /**
     * Extracts all pending data from the encoder.
     *
     *
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     *
     * Borrows heavily from https://bigflake.com/mediacodec/EncodeAndMuxTest.java.txt
     */
    private fun drainCodec(endOfStream: Boolean) {
        options.logger.log(DEBUG, "[Encoder]: drainCodec($endOfStream)")
        if (endOfStream) {
            options.logger.log(DEBUG, "[Encoder]: sending EOS to encoder")
            mediaCodec.signalEndOfInputStream()
        }
        var encoderOutputBuffers: Array<ByteBuffer?>? = mediaCodec.outputBuffers
        while (true) {
            val encoderStatus: Int = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break // out of while
                } else {
                    options.logger.log(DEBUG, "[Encoder]: no output available, spinning to await EOS")
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mediaCodec.outputBuffers
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (frameMuxer.isStarted()) {
                    throw RuntimeException("format changed twice")
                }
                val newFormat: MediaFormat = mediaCodec.outputFormat
                options.logger.log(DEBUG, "[Encoder]: encoder output format changed: $newFormat")

                // now that we have the Magic Goodies, start the muxer
                frameMuxer.start(newFormat)
            } else if (encoderStatus < 0) {
                options.logger.log(DEBUG, "[Encoder]: unexpected result from encoder.dequeueOutputBuffer: $encoderStatus")
                // let's ignore it
            } else {
                val encodedData = encoderOutputBuffers?.get(encoderStatus)
                    ?: throw RuntimeException("encoderOutputBuffer $encoderStatus was null")
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    options.logger.log(DEBUG, "[Encoder]: ignoring BUFFER_FLAG_CODEC_CONFIG")
                    bufferInfo.size = 0
                }
                if (bufferInfo.size != 0) {
                    if (!frameMuxer.isStarted()) {
                        throw RuntimeException("muxer hasn't started")
                    }
                    frameMuxer.muxVideoFrame(encodedData, bufferInfo)
                    options.logger.log(DEBUG, "[Encoder]: sent ${bufferInfo.size} bytes to muxer")
                }
                mediaCodec.releaseOutputBuffer(encoderStatus, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (!endOfStream) {
                        options.logger.log(DEBUG, "[Encoder]: reached end of stream unexpectedly")
                    } else {
                        options.logger.log(DEBUG, "[Encoder]: end of stream reached")
                    }
                    break // out of while
                }
            }
        }
    }

    fun release() {
        onClose?.invoke()
        drainCodec(true)
        mediaCodec.stop()
        mediaCodec.release()
        surface?.release()

        frameMuxer.release()
    }
}

@TargetApi(24)
internal data class MuxerConfig(
    val file: File,
    val recorderConfig: ScreenshotRecorderConfig,
    val bitrate: Int = 20_000,
    val frameRate: Float = recorderConfig.frameRate.toFloat(),
    val mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC,
    val frameMuxer: SimpleFrameMuxer = SimpleMp4FrameMuxer(file.absolutePath, frameRate)
)
