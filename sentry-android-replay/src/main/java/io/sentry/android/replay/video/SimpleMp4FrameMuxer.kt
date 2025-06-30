/**
 * Adapted from
 * https://github.com/fzyzcjy/flutter_screen_recorder/blob/dce41cec25c66baf42c6bac4198e95874ce3eb9d/packages/fast_screen_recorder/android/src/main/kotlin/com/cjy/fast_screen_recorder/SimpleMp4FrameMuxer.kt
 *
 * Copyright (c) 2021 fzyzcjy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * In addition to the standard MIT license, this library requires the following: The recorder itself
 * only saves data on user's phone locally, thus it does not have any privacy problem. However, if
 * you are going to get the records out of the local storage (e.g. upload the records to your
 * server), please explicitly ask the user for permission, and promise to only use the records to
 * debug your app. This is a part of the license of this library.
 */
package io.sentry.android.replay.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.concurrent.TimeUnit.MILLISECONDS

internal class SimpleMp4FrameMuxer(path: String, fps: Float) : SimpleFrameMuxer {
  private val frameDurationUsec: Long = (TimeUnit.SECONDS.toMicros(1L) / fps).toLong()

  private val muxer: MediaMuxer = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

  private var started = false
  private var videoTrackIndex = 0
  private var videoFrames = 0
  private var finalVideoTime: Long = 0

  override fun isStarted(): Boolean = started

  override fun start(videoFormat: MediaFormat) {
    videoTrackIndex = muxer.addTrack(videoFormat)
    muxer.start()
    started = true
  }

  override fun muxVideoFrame(encodedData: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
    // This code will break if the encoder supports B frames.
    // Ideally we would use set the value in the encoder,
    // don't know how to do that without using OpenGL
    finalVideoTime = frameDurationUsec * videoFrames++
    bufferInfo.presentationTimeUs = finalVideoTime

    //        encodedData.position(bufferInfo.offset)
    //        encodedData.limit(bufferInfo.offset + bufferInfo.size)

    muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
  }

  override fun release() {
    muxer.stop()
    muxer.release()
  }

  override fun getVideoTime(): Long {
    if (videoFrames == 0) {
      return 0
    }
    // have to add one sec as we calculate it 0-based above
    return MILLISECONDS.convert(finalVideoTime + frameDurationUsec, MICROSECONDS)
  }
}
