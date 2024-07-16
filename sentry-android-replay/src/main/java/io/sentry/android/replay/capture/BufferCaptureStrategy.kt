package io.sentry.android.replay.capture

import android.graphics.Bitmap
import android.view.MotionEvent
import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent.ReplayType.BUFFER
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.util.sample
import io.sentry.android.replay.util.submitSafely
import io.sentry.protocol.SentryId
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.FileUtils
import java.io.File
import java.security.SecureRandom

internal class BufferCaptureStrategy(
    private val options: SentryOptions,
    private val hub: IHub?,
    private val dateProvider: ICurrentDateProvider,
    private val random: SecureRandom,
    replayCacheProvider: ((replayId: SentryId, recorderConfig: ScreenshotRecorderConfig) -> ReplayCache)? = null
) : BaseCaptureStrategy(options, hub, dateProvider, replayCacheProvider = replayCacheProvider) {

    private val bufferedSegments = mutableListOf<ReplaySegment.Created>()
    private val bufferedScreensLock = Any()
    private val bufferedScreens = mutableListOf<Pair<String, Long>>()

    internal companion object {
        private const val TAG = "BufferCaptureStrategy"
    }

    override fun start(
        recorderConfig: ScreenshotRecorderConfig,
        segmentId: Int,
        replayId: SentryId,
        cleanupOldReplays: Boolean
    ) {
        super.start(recorderConfig, segmentId, replayId, cleanupOldReplays)

        hub?.configureScope {
            val screen = it.screen
            if (screen != null) {
                synchronized(bufferedScreensLock) {
                    bufferedScreens.add(screen to dateProvider.currentTimeMillis)
                }
            }
        }
    }

    override fun onScreenChanged(screen: String?) {
        synchronized(bufferedScreensLock) {
            val lastKnownScreen = bufferedScreens.lastOrNull()?.first
            if (screen != null && lastKnownScreen != screen) {
                bufferedScreens.add(screen to dateProvider.currentTimeMillis)
            }
        }
    }

    override fun stop() {
        val replayCacheDir = cache?.replayCacheDir
        replayExecutor.submitSafely(options, "$TAG.stop") {
            FileUtils.deleteRecursively(replayCacheDir)
        }
        super.stop()
    }

    override fun sendReplayForEvent(
        isCrashed: Boolean,
        eventId: String?,
        hint: Hint?,
        onSegmentSent: () -> Unit
    ) {
        val sampled = random.sample(options.experimental.sessionReplay.errorSampleRate)

        if (!sampled) {
            options.logger.log(INFO, "Replay wasn't sampled by errorSampleRate, not capturing for event %s", eventId)
            return
        }

        // write replayId to scope right away, so it gets picked up by the event that caused buffer
        // to flush
        hub?.configureScope {
            it.replayId = currentReplayId
        }

        val errorReplayDuration = options.experimental.sessionReplay.errorReplayDuration
        val now = dateProvider.currentTimeMillis
        val currentSegmentTimestamp = if (cache?.frames?.isNotEmpty() == true) {
            // in buffer mode we have to set the timestamp of the first frame as the actual start
            DateUtils.getDateTime(cache!!.frames.first().timestamp)
        } else {
            DateUtils.getDateTime(now - errorReplayDuration)
        }
        val segmentId = currentSegment
        val replayId = currentReplayId
        val height = recorderConfig.recordingHeight
        val width = recorderConfig.recordingWidth

        findAndSetStartScreen(currentSegmentTimestamp.time)

        replayExecutor.submitSafely(options, "$TAG.send_replay_for_event") {
            var bufferedSegment = bufferedSegments.removeFirstOrNull()
            while (bufferedSegment != null) {
                // capture without hint, so the buffered segments don't trigger flush notification
                bufferedSegment.capture(hub)
                bufferedSegment = bufferedSegments.removeFirstOrNull()
                Thread.sleep(100L)
            }
            val segment =
                createSegment(
                    now - currentSegmentTimestamp.time,
                    currentSegmentTimestamp,
                    replayId,
                    segmentId,
                    height,
                    width,
                    BUFFER
                )
            if (segment is ReplaySegment.Created) {
                segment.capture(hub, hint ?: Hint())

                // we only want to increment segment_id in the case of success, but currentSegment
                // might be irrelevant since we changed strategies, so in the callback we increment
                // it on the new strategy already
                onSegmentSent()
            }
        }
    }

    override fun onScreenshotRecorded(bitmap: Bitmap?, store: ReplayCache.(frameTimestamp: Long) -> Unit) {
        // have to do it before submitting, otherwise if the queue is busy, the timestamp won't be
        // reflecting the exact time of when it was captured
        val frameTimestamp = dateProvider.currentTimeMillis
        replayExecutor.submitSafely(options, "$TAG.add_frame") {
            cache?.store(frameTimestamp)

            val now = dateProvider.currentTimeMillis
            val bufferLimit = now - options.experimental.sessionReplay.errorReplayDuration
            cache?.rotate(bufferLimit)

            var removed = false
            bufferedSegments.removeAll {
                // it can be that the buffered segment is half-way older than the buffer limit, but
                // we only drop it if its end timestamp is older
                if (it.replay.timestamp.time < bufferLimit) {
                    currentSegment--
                    deleteFile(it.replay.videoFile)
                    removed = true
                    return@removeAll true
                }
                return@removeAll false
            }
            if (removed) {
                // shift segmentIds after rotating buffered segments
                bufferedSegments.forEachIndexed { index, segment ->
                    segment.setSegmentId(index)
                }
            }
        }
    }

    private fun deleteFile(file: File?) {
        if (file == null) {
            return
        }
        try {
            if (!file.delete()) {
                options.logger.log(ERROR, "Failed to delete replay segment: %s", file.absolutePath)
            }
        } catch (e: Throwable) {
            options.logger.log(ERROR, e, "Failed to delete replay segment: %s", file.absolutePath)
        }
    }

    override fun onConfigurationChanged(recorderConfig: ScreenshotRecorderConfig) {
        val errorReplayDuration = options.experimental.sessionReplay.errorReplayDuration
        val now = dateProvider.currentTimeMillis
        val currentSegmentTimestamp = if (cache?.frames?.isNotEmpty() == true) {
            // in buffer mode we have to set the timestamp of the first frame as the actual start
            DateUtils.getDateTime(cache!!.frames.first().timestamp)
        } else {
            DateUtils.getDateTime(now - errorReplayDuration)
        }
        val segmentId = currentSegment
        val duration = now - currentSegmentTimestamp.time
        val replayId = currentReplayId
        val height = this.recorderConfig.recordingHeight
        val width = this.recorderConfig.recordingWidth
        replayExecutor.submitSafely(options, "$TAG.onConfigurationChanged") {
            val segment =
                createSegment(duration, currentSegmentTimestamp, replayId, segmentId, height, width, BUFFER)
            if (segment is ReplaySegment.Created) {
                bufferedSegments += segment

                currentSegment++
            }
        }
        super.onConfigurationChanged(recorderConfig)
    }

    override fun convert(): CaptureStrategy {
        // we hand over replayExecutor to the new strategy to preserve order of execution
        val captureStrategy = SessionCaptureStrategy(options, hub, dateProvider, replayExecutor)
        captureStrategy.start(recorderConfig, segmentId = currentSegment, replayId = currentReplayId, cleanupOldReplays = false)
        return captureStrategy
    }

    override fun onTouchEvent(event: MotionEvent) {
        super.onTouchEvent(event)
        val bufferLimit = dateProvider.currentTimeMillis - options.experimental.sessionReplay.errorReplayDuration
        rotateEvents(currentEvents, bufferLimit)
    }

    private fun findAndSetStartScreen(segmentStart: Long) {
        synchronized(bufferedScreensLock) {
            val startScreen = bufferedScreens.lastOrNull { (_, timestamp) ->
                timestamp <= segmentStart
            }?.first
            // if no screen is found before the segment start, this likely means the buffer is from the
            // app start, and the start screen will be taken from the navigation crumbs
            if (startScreen != null) {
                screenAtStart = startScreen
            }
            // can clear as we switch to session mode and don't care anymore about buffering
            bufferedSegments.clear()
        }
    }
}
