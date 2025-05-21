package io.sentry.android.replay

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat.JPEG
import android.graphics.BitmapFactory
import io.sentry.DateUtils
import io.sentry.ReplayRecording
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryLevel.WARNING
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.SentryReplayEvent.ReplayType.SESSION
import io.sentry.android.replay.video.MuxerConfig
import io.sentry.android.replay.video.SimpleVideoEncoder
import io.sentry.protocol.SentryId
import io.sentry.rrweb.RRWebEvent
import io.sentry.util.AutoClosableReentrantLock
import io.sentry.util.FileUtils
import java.io.Closeable
import java.io.File
import java.io.StringReader
import java.util.Date
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean

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
public class ReplayCache(
    private val options: SentryOptions,
    private val replayId: SentryId
) : Closeable {

    private val isClosed = AtomicBoolean(false)
    private val encoderLock = AutoClosableReentrantLock()
    private val lock = AutoClosableReentrantLock()
    private var encoder: SimpleVideoEncoder? = null

    internal val replayCacheDir: File? by lazy {
        makeReplayCacheDir(options, replayId)
    }

    // TODO: maybe account for multi-threaded access
    internal val frames = mutableListOf<ReplayFrame>()

    private val ongoingSegment = LinkedHashMap<String, String>()
    internal val ongoingSegmentFile: File? by lazy {
        if (replayCacheDir == null) {
            return@lazy null
        }

        val file = File(replayCacheDir, ONGOING_SEGMENT)
        if (!file.exists()) {
            file.createNewFile()
        }
        file
    }

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
    internal fun addFrame(bitmap: Bitmap, frameTimestamp: Long, screen: String? = null) {
        if (replayCacheDir == null || bitmap.isRecycled) {
            return
        }
        replayCacheDir?.mkdirs()

        val screenshot = File(replayCacheDir, "$frameTimestamp.jpg").also {
            it.createNewFile()
        }
        screenshot.outputStream().use {
            bitmap.compress(JPEG, options.sessionReplay.quality.screenshotQuality, it)
            it.flush()
        }

        addFrame(screenshot, frameTimestamp, screen)
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
    public fun addFrame(screenshot: File, frameTimestamp: Long, screen: String? = null) {
        val frame = ReplayFrame(screenshot, frameTimestamp, screen)
        frames += frame
    }

    private var failed = false

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
     * @param height desired height of the video in pixels (e.g. it can change from the initial one
     * in case of window resize or orientation change)
     * @param width desired width of the video in pixels (e.g. it can change from the initial one
     * in case of window resize or orientation change)
     * @param videoFile optional, location of the file to store the result video. If this is
     * provided, [segmentId] from above is disregarded and not used.
     * @return a generated video of type [GeneratedVideo], which contains the resulting video file
     * location, frame count and duration in milliseconds.
     */
    public fun createVideoOf(
        duration: Long,
        from: Long,
        segmentId: Int,
        height: Int,
        width: Int,
        frameRate: Int,
        bitRate: Int,
        videoFile: File = File(replayCacheDir, "$segmentId.mp4")
    ): GeneratedVideo? {
        if (videoFile.exists() && videoFile.length() > 0) {
            videoFile.delete()
        }
        if (frames.isEmpty()) {
            options.logger.log(
                DEBUG,
                "No captured frames, skipping generating a video segment"
            )
            return null
        }

        encoder = encoderLock.acquire().use {
            SimpleVideoEncoder(
                options,
                MuxerConfig(
                    file = videoFile,
                    recordingHeight = height,
                    recordingWidth = width,
                    frameRate = frameRate,
                    bitRate = bitRate
                )
            ).also { it.start() }
        }

        val step = 1000 / frameRate.toLong()
        var frameCount = 0
        var lastFrame: ReplayFrame? = frames.first()
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
            if (encode(lastFrame) /* && (segmentId != 1 || failed)*/) {
                frameCount++
            } else if (lastFrame != null) {
                // if we failed to encode the frame, we delete the screenshot right away as the
                // likelihood of it being able to be encoded later is low
                deleteFile(lastFrame.screenshot)
                frames.remove(lastFrame)
                lastFrame = null
            }
        }

        if (frameCount == 0) {
            failed = true
            options.logger.log(
                DEBUG,
                "Generated a video with no frames, not capturing a replay segment"
            )
            deleteFile(videoFile)
            return null
        }

        var videoDuration: Long
        encoderLock.acquire().use {
            encoder?.release()
            videoDuration = encoder?.duration ?: 0
            encoder = null
        }

        rotate(until = (from + duration))

        return GeneratedVideo(videoFile, frameCount, videoDuration)
    }

    private fun encode(frame: ReplayFrame?): Boolean {
        if (frame == null) {
            return false
        }
        return try {
            val bitmap = BitmapFactory.decodeFile(frame.screenshot.absolutePath)
            encoderLock.acquire().use {
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

    /**
     * Removes frames from the in-memory and disk cache from start to [until].
     *
     * @param until value until whose the frames should be removed, represented as unix timestamp
     * @return the first screen in the rotated buffer, if any
     */
    internal fun rotate(until: Long): String? {
        var screen: String? = null
        frames.removeAll {
            if (it.timestamp < until) {
                deleteFile(it.screenshot)
                return@removeAll true
            } else if (screen == null) {
                screen = it.screen
            }
            return@removeAll false
        }
        return screen
    }

    override fun close() {
        encoderLock.acquire().use {
            encoder?.release()
            encoder = null
        }
        isClosed.set(true)
    }

    // TODO: it's awful, choose a better serialization format
    internal fun persistSegmentValues(key: String, value: String?) {
        lock.acquire().use {
            if (isClosed.get()) {
                return
            }
            if (ongoingSegmentFile?.exists() != true) {
                ongoingSegmentFile?.createNewFile()
            }
            if (ongoingSegment.isEmpty()) {
                ongoingSegmentFile?.useLines { lines ->
                    lines.associateTo(ongoingSegment) {
                        val (k, v) = it.split("=", limit = 2)
                        k to v
                    }
                }
            }
            if (value == null) {
                ongoingSegment.remove(key)
            } else {
                ongoingSegment[key] = value
            }
            ongoingSegmentFile?.writeText(ongoingSegment.entries.joinToString("\n") { (k, v) -> "$k=$v" })
        }
    }

    internal companion object {
        internal const val ONGOING_SEGMENT = ".ongoing_segment"

        internal const val SEGMENT_KEY_HEIGHT = "config.height"
        internal const val SEGMENT_KEY_WIDTH = "config.width"
        internal const val SEGMENT_KEY_FRAME_RATE = "config.frame-rate"
        internal const val SEGMENT_KEY_BIT_RATE = "config.bit-rate"
        internal const val SEGMENT_KEY_TIMESTAMP = "segment.timestamp"
        internal const val SEGMENT_KEY_REPLAY_ID = "replay.id"
        internal const val SEGMENT_KEY_REPLAY_TYPE = "replay.type"
        internal const val SEGMENT_KEY_REPLAY_SCREEN_AT_START = "replay.screen-at-start"
        internal const val SEGMENT_KEY_REPLAY_RECORDING = "replay.recording"
        internal const val SEGMENT_KEY_ID = "segment.id"

        fun makeReplayCacheDir(options: SentryOptions, replayId: SentryId): File? {
            return if (options.cacheDirPath.isNullOrEmpty()) {
                options.logger.log(
                    WARNING,
                    "SentryOptions.cacheDirPath is not set, session replay is no-op"
                )
                null
            } else {
                File(options.cacheDirPath!!, "replay_$replayId").also { it.mkdirs() }
            }
        }

        internal fun fromDisk(options: SentryOptions, replayId: SentryId, replayCacheProvider: ((replayId: SentryId) -> ReplayCache)? = null): LastSegmentData? {
            val replayCacheDir = makeReplayCacheDir(options, replayId)
            val lastSegmentFile = File(replayCacheDir, ONGOING_SEGMENT)
            if (!lastSegmentFile.exists()) {
                options.logger.log(DEBUG, "No ongoing segment found for replay: %s", replayId)
                FileUtils.deleteRecursively(replayCacheDir)
                return null
            }

            val lastSegment = LinkedHashMap<String, String>()
            lastSegmentFile.useLines { lines ->
                lines.associateTo(lastSegment) {
                    val (k, v) = it.split("=", limit = 2)
                    k to v
                }
            }

            val height = lastSegment[SEGMENT_KEY_HEIGHT]?.toIntOrNull()
            val width = lastSegment[SEGMENT_KEY_WIDTH]?.toIntOrNull()
            val frameRate = lastSegment[SEGMENT_KEY_FRAME_RATE]?.toIntOrNull()
            val bitRate = lastSegment[SEGMENT_KEY_BIT_RATE]?.toIntOrNull()
            val segmentId = lastSegment[SEGMENT_KEY_ID]?.toIntOrNull()
            val segmentTimestamp = try {
                DateUtils.getDateTime(lastSegment[SEGMENT_KEY_TIMESTAMP].orEmpty())
            } catch (e: Throwable) {
                null
            }
            val replayType = try {
                ReplayType.valueOf(lastSegment[SEGMENT_KEY_REPLAY_TYPE].orEmpty())
            } catch (e: Throwable) {
                null
            }
            if (height == null || width == null || frameRate == null || bitRate == null ||
                (segmentId == null || segmentId == -1) || segmentTimestamp == null || replayType == null
            ) {
                options.logger.log(
                    DEBUG,
                    "Incorrect segment values found for replay: %s, deleting the replay",
                    replayId
                )
                FileUtils.deleteRecursively(replayCacheDir)
                return null
            }

            val recorderConfig = ScreenshotRecorderConfig(
                recordingHeight = height,
                recordingWidth = width,
                frameRate = frameRate,
                bitRate = bitRate,
                // these are not used for already captured frames, so we just hardcode them
                scaleFactorX = 1.0f,
                scaleFactorY = 1.0f
            )

            val cache = replayCacheProvider?.invoke(replayId) ?: ReplayCache(options, replayId)
            cache.replayCacheDir?.listFiles { dir, name ->
                if (name.endsWith(".jpg")) {
                    val file = File(dir, name)
                    val timestamp = file.nameWithoutExtension.toLongOrNull()
                    if (timestamp != null) {
                        cache.addFrame(file, timestamp)
                    }
                }
                false
            }

            if (cache.frames.isEmpty()) {
                options.logger.log(
                    DEBUG,
                    "No frames found for replay: %s, deleting the replay",
                    replayId
                )
                FileUtils.deleteRecursively(replayCacheDir)
                return null
            }

            cache.frames.sortBy { it.timestamp }
            // TODO: this should be removed when we start sending buffered segments on next launch
            val normalizedSegmentId = if (replayType == SESSION) segmentId else 0
            val normalizedTimestamp = if (replayType == SESSION) {
                segmentTimestamp
            } else {
                // in buffer mode we have to set the timestamp of the first frame as the actual start
                DateUtils.getDateTime(cache.frames.first().timestamp)
            }

            // add one frame to include breadcrumbs/events happened after the frame was captured
            val duration = cache.frames.last().timestamp - normalizedTimestamp.time + (1000 / frameRate)

            val events = lastSegment[SEGMENT_KEY_REPLAY_RECORDING]?.let {
                val reader = StringReader(it)
                val recording = options.serializer.deserialize(reader, ReplayRecording::class.java)
                if (recording?.payload != null) {
                    LinkedList(recording.payload!!)
                } else {
                    null
                }
            } ?: emptyList()

            return LastSegmentData(
                recorderConfig = recorderConfig,
                cache = cache,
                timestamp = normalizedTimestamp,
                id = normalizedSegmentId,
                duration = duration,
                replayType = replayType,
                screenAtStart = lastSegment[SEGMENT_KEY_REPLAY_SCREEN_AT_START],
                events = events.sortedBy { it.timestamp }
            )
        }
    }
}

internal data class LastSegmentData(
    val recorderConfig: ScreenshotRecorderConfig,
    val cache: ReplayCache,
    val timestamp: Date,
    val id: Int,
    val duration: Long,
    val replayType: ReplayType,
    val screenAtStart: String?,
    val events: List<RRWebEvent>
)

internal data class ReplayFrame(
    val screenshot: File,
    val timestamp: Long,
    val screen: String? = null
)

public data class GeneratedVideo(
    val video: File,
    val frameCount: Int,
    val duration: Long
)
