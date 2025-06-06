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
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryOptions
import io.sentry.android.replay.util.SystemProperties
import java.io.File
import java.nio.ByteBuffer
import kotlin.LazyThreadSafetyMode.NONE

private const val TIMEOUT_USEC = 100_000L

@TargetApi(26)
internal class SimpleVideoEncoder(
    val options: SentryOptions,
    val muxerConfig: MuxerConfig,
    val onClose: (() -> Unit)? = null
) {

    private val hasExynosCodec: Boolean by lazy(NONE) {
        // MediaCodecList ctor will initialize an internal in-memory static cache of codecs, so this
        // call is only expensive the first time
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .any { it.name.contains("c2.exynos") }
    }

    internal val mediaCodec: MediaCodec = run {
        // c2.exynos.h264.encoder seems to have problems encoding the video (Pixel and Samsung devices)
        // so we use the default encoder instead
        val codec = if (hasExynosCodec) {
            MediaCodec.createByCodecName("c2.android.avc.encoder")
        } else {
            MediaCodec.createEncoderByType(muxerConfig.mimeType)
        }

        codec
    }

    private val mediaFormat: MediaFormat by lazy(NONE) {
        var bitRate = muxerConfig.bitRate

        try {
            val videoCapabilities = mediaCodec.codecInfo
                .getCapabilitiesForType(muxerConfig.mimeType)
                .videoCapabilities

            if (!videoCapabilities.bitrateRange.contains(bitRate)) {
                options.logger.log(
                    DEBUG,
                    "Encoder doesn't support the provided bitRate: $bitRate, the value will be clamped to the closest one"
                )
                bitRate = videoCapabilities.bitrateRange.clamp(bitRate)
            }
        } catch (e: Throwable) {
            options.logger.log(DEBUG, "Could not retrieve MediaCodec info", e)
        }

        // TODO: if this ever becomes a problem, move this to ScreenshotRecorderConfig.from()
        // TODO: because the screenshot config has to match the video config

//        var frameRate = muxerConfig.recorderConfig.frameRate
//        if (!videoCapabilities.supportedFrameRates.contains(frameRate)) {
//            options.logger.log(DEBUG, "Encoder doesn't support the provided frameRate: $frameRate, the value will be clamped to the closest one")
//            frameRate = videoCapabilities.supportedFrameRates.clamp(frameRate)
//        }

//        var height = muxerConfig.recorderConfig.recordingHeight
//        var width = muxerConfig.recorderConfig.recordingWidth
//        val aspectRatio = height.toFloat() / width.toFloat()
//        while (!videoCapabilities.supportedHeights.contains(height) || !videoCapabilities.supportedWidths.contains(width)) {
//            options.logger.log(DEBUG, "Encoder doesn't support the provided height x width: ${height}x${width}, the values will be clamped to the closest ones")
//            if (!videoCapabilities.supportedHeights.contains(height)) {
//                height = videoCapabilities.supportedHeights.clamp(height)
//                width = (height / aspectRatio).roundToInt()
//            } else if (!videoCapabilities.supportedWidths.contains(width)) {
//                width = videoCapabilities.supportedWidths.clamp(width)
//                height = (width * aspectRatio).roundToInt()
//            }
//        }

        val format = MediaFormat.createVideoFormat(
            muxerConfig.mimeType,
            muxerConfig.recordingWidth,
            muxerConfig.recordingHeight
        )

        // this allows reducing bitrate on newer devices, where they enforce higher quality in VBR
        // mode, see https://developer.android.com/reference/android/media/MediaCodec#qualityFloor
        // TODO: maybe enable this back later, for now variable bitrate seems to provide much better
        // TODO: quality with almost no overhead in terms of video size, let's monitor that
//        format.setInteger(
//            MediaFormat.KEY_BITRATE_MODE,
//            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
//        )
        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setFloat(MediaFormat.KEY_FRAME_RATE, muxerConfig.frameRate.toFloat())
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 6) // use 6 to force non-key frames, meaning only partial updates to save the video size. Every 6th second is a key frame, which is useful for buffer mode

        format
    }

    private val bufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()
    private val frameMuxer = SimpleMp4FrameMuxer(muxerConfig.file.absolutePath, muxerConfig.frameRate.toFloat())
    val duration get() = frameMuxer.getVideoTime()

    private var surface: Surface? = null

    fun start() {
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = mediaCodec.createInputSurface()
        mediaCodec.start()
        drainCodec(false)
    }

    fun encode(image: Bitmap) {
        /** it seems that Xiaomi devices have problems with hardware canvas, so we have to use
         * lockCanvas instead https://stackoverflow.com/a/73520742
         * ---
         * Same for Motorola devices.
         * ---
         * As for the T606, it's a Spreadtrum/Unisoc chipset and can be spread across various
         * devices, so we have to check the SOC_MODEL property, as the manufacturer name might have
         * changed.
         * https://github.com/getsentry/sentry-android-gradle-plugin/issues/861#issuecomment-2867021256
         */
        val canvas = if (
            Build.MANUFACTURER.contains("xiaomi", ignoreCase = true) ||
            Build.MANUFACTURER.contains("motorola", ignoreCase = true) ||
            SystemProperties.gegt(SystemProperties.SOC_MODEL).equals("T606", ignoreCase = true)
        ) {
            surface?.lockCanvas(null)
        } else {
            surface?.lockHardwareCanvas()
        }
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
        if (options.sessionReplay.isDebug) {
            options.logger.log(DEBUG, "[Encoder]: drainCodec($endOfStream)")
        }
        if (endOfStream) {
            if (options.sessionReplay.isDebug) {
                options.logger.log(DEBUG, "[Encoder]: sending EOS to encoder")
            }
            mediaCodec.signalEndOfInputStream()
        }
        var encoderOutputBuffers: Array<ByteBuffer?>? = mediaCodec.outputBuffers
        while (true) {
            val encoderStatus: Int = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break // out of while
                } else if (options.sessionReplay.isDebug) {
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
                if (options.sessionReplay.isDebug) {
                    options.logger.log(DEBUG, "[Encoder]: encoder output format changed: $newFormat")
                }

                // now that we have the Magic Goodies, start the muxer
                frameMuxer.start(newFormat)
            } else if (encoderStatus < 0) {
                if (options.sessionReplay.isDebug) {
                    options.logger.log(DEBUG, "[Encoder]: unexpected result from encoder.dequeueOutputBuffer: $encoderStatus")
                }
                // let's ignore it
            } else {
                val encodedData = encoderOutputBuffers?.get(encoderStatus)
                    ?: throw RuntimeException("encoderOutputBuffer $encoderStatus was null")
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (options.sessionReplay.isDebug) {
                        options.logger.log(DEBUG, "[Encoder]: ignoring BUFFER_FLAG_CODEC_CONFIG")
                    }
                    bufferInfo.size = 0
                }
                if (bufferInfo.size != 0) {
                    if (!frameMuxer.isStarted()) {
                        throw RuntimeException("muxer hasn't started")
                    }
                    frameMuxer.muxVideoFrame(encodedData, bufferInfo)
                    if (options.sessionReplay.isDebug) {
                        options.logger.log(DEBUG, "[Encoder]: sent ${bufferInfo.size} bytes to muxer")
                    }
                }
                mediaCodec.releaseOutputBuffer(encoderStatus, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (options.sessionReplay.isDebug) {
                        if (!endOfStream) {
                            options.logger.log(
                                DEBUG,
                                "[Encoder]: reached end of stream unexpectedly"
                            )
                        } else {
                            options.logger.log(DEBUG, "[Encoder]: end of stream reached")
                        }
                    }
                    break // out of while
                }
            }
        }
    }

    fun release() {
        try {
            onClose?.invoke()
            drainCodec(true)
            mediaCodec.stop()
            mediaCodec.release()
            surface?.release()

            frameMuxer.release()
        } catch (e: Throwable) {
            options.logger.log(DEBUG, "Failed to properly release video encoder", e)
        }
    }
}

@TargetApi(24)
internal data class MuxerConfig(
    val file: File,
    var recordingWidth: Int,
    var recordingHeight: Int,
    val frameRate: Int,
    val bitRate: Int,
    val mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC
)
