package io.sentry.android.replay.video

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaCodecList.REGULAR_CODECS
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import io.sentry.android.replay.video.SimpleFrameMuxer
import io.sentry.android.replay.video.SimpleMp4FrameMuxer
import java.io.File

class SimpleVideoEncoder(
  val muxerConfig: MuxerConfig,
) {
  companion object {
    const val TAG = "SimpleVideoEncoder"
  }

  private val mediaFormat: MediaFormat = run {
    Log.i(TAG, "mediaFormat creation begin")

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

    Log.i(TAG, "mediaFormat creation end format=$format")

    format
  }

  private val mediaCodec: MediaCodec = run {
    Log.i(TAG, "mediaCodec creation begin")

//    val codecs = MediaCodecList(REGULAR_CODECS)
//    val codecName = codecs.findEncoderForFormat(mediaFormat)
//    val codec = MediaCodec.createByCodecName(codecName)
    val codec = MediaCodec.createEncoderByType(muxerConfig.mimeType)

    Log.i(TAG, "mediaCodec creation end codec=$codec")

    codec
  }

  private val frameMuxer = muxerConfig.frameMuxer

  private var surface: Surface? = null

  fun start() {
    mediaCodec.setCallback(createMediaCodecCallback(), Handler(Looper.getMainLooper()))

    mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    surface = mediaCodec.createInputSurface()
    mediaCodec.start()

//        drainCodec(false)
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
        // need to catch, since this is from callback, so there are no
        // things like pigeon auto-catch
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
            Log.i(TAG, "drainCodec end of stream reached")
            actualRelease()
          }
      }

      override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        Log.e(TAG, "onError (MediaCodec.Callback)", e)
      }

      override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
          Log.i(TAG, "onOutputFormatChanged format=$format")

          // should happen before receiving buffers, and should only happen once
          if (frameMuxer.isStarted()) {
            throw RuntimeException("format changed twice")
          }
          val newFormat: MediaFormat = mediaCodec.outputFormat
          Log.i(TAG, "encoder output format changed: $newFormat")

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
//        drainCodec(true)
    mediaCodec.signalEndOfInputStream()
  }

  private fun actualRelease() {
    mediaCodec.stop()
    mediaCodec.release()
    surface?.release()

    frameMuxer.release()
  }
}

data class MuxerConfig(
  val file: File,
  val videoWidth: Int,
  val videoHeight: Int,
  val mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC,
  val frameRate: Float,
  val bitrate: Int,
  val frameMuxer: SimpleFrameMuxer = SimpleMp4FrameMuxer(file.absolutePath, frameRate),
)
