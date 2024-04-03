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

/**
 * A basic in-memory and disk cache for Session Replay frames. Frames are stored in order under the
 * [SentryOptions.cacheDirPath] + [replayId] folder. The class is also capable of creating an mp4
 * video segment out of the stored frames, provided start time and duration using the available
 * on-device [android.media.MediaCodec].
 *
 * This class is not thread-safe, meaning, [addFrame] cannot be called concurrently with
 * [createVideoOf], and they should be invoked from the same thread.
 *
 * @param options SentryOptions instance, used for logging and cacheDir
 * @param replayId the current replay id, used for giving a unique name to the replay folder
 * @param recorderConfig ScreenshotRecorderConfig, used for video resolution and frame-rate
 */
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

    /**
     * Stores the current frame screenshot to in-memory cache as well as disk with [frameTimestamp]
     * as filename. Uses [Bitmap.CompressFormat.JPEG] format with quality 80. The frames are stored
     * under [replayCacheDir].
     *
     * This method is not thread-safe.
     *
     * @param bitmap the frame screenshot
     * @param frameTimestamp the timestamp when the frame screenshot was taken
     */
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

    /**
     * Same as [addFrame], but accepts frame screenshot as [File], the file should contain
     * a bitmap/image by the time [createVideoOf] is invoked.
     *
     * This method is not thread-safe.
     *
     * @param screenshot file containing the frame screenshot
     * @param frameTimestamp the timestamp when the frame screenshot was taken
     */
    public fun addFrame(screenshot: File, frameTimestamp: Long) {
        val frame = ReplayFrame(screenshot, frameTimestamp)
        frames += frame
    }

    /**
     * Creates a video out of currently stored [frames] given the start time and duration using the
     * on-device codecs [android.media.MediaCodec]. The generated video will be stored in
     * [videoFile] location, which defaults to "[replayCacheDir]/[segmentId].mp4".
     *
     * This method is not thread-safe.
     *
     * @param duration desired video duration in milliseconds
     * @param from desired start of the video represented as unix timestamp in milliseconds
     * @param segmentId current segment id, used for inferring the filename to store the
     * result video under [replayCacheDir], e.g. "replay_<uuid>/0.mp4", where segmentId=0
     * @param videoFile optional, location of the file to store the result video. If this is
     * provided, [segmentId] from above is disregarded and not used.
     * @return a generated video of type [GeneratedVideo], which contains the resulting video file
     * location, frame count and duration in milliseconds.
     */
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
