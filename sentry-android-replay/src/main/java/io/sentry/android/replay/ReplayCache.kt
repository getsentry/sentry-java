package io.sentry.android.replay

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat.JPEG
import android.graphics.BitmapFactory
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryLevel.WARNING
import io.sentry.SentryOptions
import io.sentry.android.replay.video.MuxerConfig
import io.sentry.android.replay.video.SimpleVideoEncoder
import io.sentry.protocol.SentryId
import java.io.Closeable
import java.io.File

public class ReplayCache internal constructor(
    private val options: SentryOptions,
    private val replayId: SentryId,
    private val recorderConfig: ScreenshotRecorderConfig,
    private val encoderCreator: (File) -> SimpleVideoEncoder
) : Closeable {

    public constructor(
        options: SentryOptions,
        replayId: SentryId,
        recorderConfig: ScreenshotRecorderConfig
    ) : this(options, replayId, recorderConfig, encoderCreator = { videoFile ->
        SimpleVideoEncoder(
            options,
            MuxerConfig(
                file = videoFile,
                recorderConfig = recorderConfig,
                frameRate = recorderConfig.frameRate.toFloat(),
                bitrate = 20 * 1000
            )
        ).also { it.start() }
    })

    private val encoderLock = Any()
    private var encoder: SimpleVideoEncoder? = null

    internal val replayCacheDir: File? by lazy {
        if (options.cacheDirPath.isNullOrEmpty()) {
            options.logger.log(
                WARNING,
                "SentryOptions.cacheDirPath is not set, session replay is no-op"
            )
            null
        } else {
            File(options.cacheDirPath!!, "replay_$replayId").also { it.mkdirs() }
        }
    }

    // TODO: maybe account for multi-threaded access
    internal val frames = mutableListOf<ReplayFrame>()

    internal fun addFrame(bitmap: Bitmap, frameTimestamp: Long) {
        if (replayCacheDir == null) {
            return
        }

        val screenshot = File(replayCacheDir, "$frameTimestamp.jpg").also {
            it.createNewFile()
        }
        screenshot.outputStream().use {
            bitmap.compress(JPEG, 80, it)
            it.flush()
        }

        addFrame(screenshot, frameTimestamp)
    }

    public fun addFrame(screenshot: File, frameTimestamp: Long) {
        val frame = ReplayFrame(screenshot, frameTimestamp)
        frames += frame
    }

    public fun createVideoOf(
        duration: Long,
        from: Long,
        segmentId: Int,
        videoFile: File = File(replayCacheDir, "$segmentId.mp4")
    ): GeneratedVideo? {
        if (frames.isEmpty()) {
            options.logger.log(
                DEBUG,
                "No captured frames, skipping generating a video segment"
            )
            return null
        }

        // TODO: reuse instance of encoder and just change file path to create a different muxer
        encoder = synchronized(encoderLock) { encoderCreator(videoFile) }

        val step = 1000 / recorderConfig.frameRate.toLong()
        var frameCount = 0
        var lastFrame: ReplayFrame = frames.first()
        for (timestamp in from until (from + (duration)) step step) {
            val iter = frames.iterator()
            while (iter.hasNext()) {
                val frame = iter.next()
                if (frame.timestamp in (timestamp..timestamp + step)) {
                    lastFrame = frame
                    break // we only support 1 frame per given interval
                }

                // assuming frames are in order, if out of bounds exit early
                if (frame.timestamp > timestamp + step) {
                    break
                }
            }

            // we either encode a new frame within the step bounds or replicate the last known frame
            // to respect the video duration
            if (encode(lastFrame)) {
                frameCount++
            }
        }

        if (frameCount == 0) {
            options.logger.log(
                DEBUG,
                "Generated a video with no frames, not capturing a replay segment"
            )
            deleteFile(videoFile)
            return null
        }

        var videoDuration: Long
        synchronized(encoderLock) {
            encoder?.release()
            videoDuration = encoder?.duration ?: 0
            encoder = null
        }

        frames.removeAll {
            if (it.timestamp < (from + duration)) {
                deleteFile(it.screenshot)
                return@removeAll true
            }
            return@removeAll false
        }

        return GeneratedVideo(videoFile, frameCount, videoDuration)
    }

    private fun encode(frame: ReplayFrame): Boolean {
        return try {
            val bitmap = BitmapFactory.decodeFile(frame.screenshot.absolutePath)
            synchronized(encoderLock) {
                encoder?.encode(bitmap)
            }
            bitmap.recycle()
            true
        } catch (e: Throwable) {
            options.logger.log(WARNING, "Unable to decode bitmap and encode it into a video, skipping frame", e)
            false
        }
    }

    private fun deleteFile(file: File) {
        try {
            if (!file.delete()) {
                options.logger.log(ERROR, "Failed to delete replay frame: %s", file.absolutePath)
            }
        } catch (e: Throwable) {
            options.logger.log(ERROR, e, "Failed to delete replay frame: %s", file.absolutePath)
        }
    }

    override fun close() {
        synchronized(encoderLock) {
            encoder?.release()
            encoder = null
        }
    }
}

internal data class ReplayFrame(
    val screenshot: File,
    val timestamp: Long
)

public data class GeneratedVideo(
    val video: File,
    val frameCount: Int,
    val duration: Long
)
