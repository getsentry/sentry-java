/**
 * Adapted from https://github.com/fzyzcjy/flutter_screen_recorder/blob/master/packages/fast_screen_recorder/android/src/main/kotlin/com/cjy/fast_screen_recorder/SimpleVideoEncoder.kt
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

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.view.Surface
import java.io.File

internal class SimpleVideoEncoder(
    val muxerConfig: MuxerConfig
) {
    private val mediaFormat: MediaFormat = run {
        val format = MediaFormat.createVideoFormat(
            muxerConfig.mimeType,
            muxerConfig.videoWidth,
            muxerConfig.videoHeight
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

    private val mediaCodec: MediaCodec = run {
//    val codecs = MediaCodecList(REGULAR_CODECS)
//    val codecName = codecs.findEncoderForFormat(mediaFormat)
//    val codec = MediaCodec.createByCodecName(codecName)
        val codec = MediaCodec.createEncoderByType(muxerConfig.mimeType)

        codec
    }

    private val frameMuxer = muxerConfig.frameMuxer

    private var surface: Surface? = null

    fun start() {
        mediaCodec.setCallback(createMediaCodecCallback(), Handler(Looper.getMainLooper()))

        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = mediaCodec.createInputSurface()
        mediaCodec.start()
    }

    private fun createMediaCodecCallback(): MediaCodec.Callback {
        return object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                val encodedData = codec.getOutputBuffer(index)!!

                var effectiveSize = info.size

                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    effectiveSize = 0
                }

                if (effectiveSize != 0) {
                    if (!frameMuxer.isStarted()) {
                        throw RuntimeException("muxer hasn't started")
                    }
                    frameMuxer.muxVideoFrame(encodedData, info)
                }

                mediaCodec.releaseOutputBuffer(index, false)

                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    actualRelease()
                }
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                // should happen before receiving buffers, and should only happen once
                if (frameMuxer.isStarted()) {
                    throw RuntimeException("format changed twice")
                }
                val newFormat: MediaFormat = mediaCodec.outputFormat
                // now that we have the Magic Goodies, start the muxer
                frameMuxer.start(newFormat)
            }
        }
    }

    fun encode(image: Bitmap) {
        // NOTE do not use `lockCanvas` like what is done in bitmap2video
        // This is because https://developer.android.com/reference/android/media/MediaCodec#createInputSurface()
        // says that, "Surface.lockCanvas(android.graphics.Rect) may fail or produce unexpected results."
        val canvas = surface?.lockHardwareCanvas()
        canvas?.drawBitmap(image, 0f, 0f, null)
        surface?.unlockCanvasAndPost(canvas)
    }

    /**
     * can only *start* releasing, since it is asynchronous
     */
    fun startRelease() {
        mediaCodec.signalEndOfInputStream()
    }

    private fun actualRelease() {
        mediaCodec.stop()
        mediaCodec.release()
        surface?.release()

        frameMuxer.release()
    }
}

internal data class MuxerConfig(
    val file: File,
    val videoWidth: Int,
    val videoHeight: Int,
    val mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC,
    val frameRate: Float,
    val bitrate: Int,
    val frameMuxer: SimpleFrameMuxer = SimpleMp4FrameMuxer(file.absolutePath, frameRate)
)
