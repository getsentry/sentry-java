package io.sentry.android.replay.capture

import android.graphics.Bitmap
import android.view.MotionEvent
import io.sentry.DateUtils
import io.sentry.IHub
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent.ReplayType.BUFFER
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.capture.CaptureStrategy.Companion.rotateEvents
import io.sentry.android.replay.capture.CaptureStrategy.ReplaySegment
import io.sentry.android.replay.util.sample
import io.sentry.android.replay.util.submitSafely
import io.sentry.protocol.SentryId
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.FileUtils
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.ScheduledExecutorService

internal class BufferCaptureStrategy(
    private val options: SentryOptions,
    private val hub: IHub?,
    private val dateProvider: ICurrentDateProvider,
    private val random: SecureRandom,
    executor: ScheduledExecutorService? = null,
    replayCacheProvider: ((replayId: SentryId, recorderConfig: ScreenshotRecorderConfig) -> ReplayCache)? = null
) : BaseCaptureStrategy(options, hub, dateProvider, executor = executor, replayCacheProvider = replayCacheProvider) {

    // TODO: capture envelopes for buffered segments instead, but don't send them until buffer is triggered
    private val bufferedSegments = mutableListOf<ReplaySegment.Created>()

    // TODO: rework this bs, it doesn't work with sending replay on restart
    private val bufferedScreensLock = Any()
    private val bufferedScreens = mutableListOf<Pair<String, Long>>()

    internal companion object {
        private const val TAG = "BufferCaptureStrategy"
        private const val ENVELOPE_PROCESSING_DELAY: Long = 100L
    }

    override fun start(
        recorderConfig: ScreenshotRecorderConfig,
        segmentId: Int,
        replayId: SentryId
    ) {
        super.start(recorderConfig, segmentId, replayId)

        hub?.configureScope {
            val screen = it.screen?.substringAfterLast('.')
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

    override fun pause() {
        createCurrentSegment("pause") { segment ->
            if (segment is ReplaySegment.Created) {
                bufferedSegments += segment

                currentSegment++
            }
        }
        super.pause()
    }

    override fun stop() {
        val replayCacheDir = cache?.replayCacheDir
        replayExecutor.submitSafely(options, "$TAG.stop") {
            FileUtils.deleteRecursively(replayCacheDir)
        }
        super.stop()
    }

    override fun captureReplay(
        isTerminating: Boolean,
        onSegmentSent: () -> Unit
    ) {
        val sampled = random.sample(options.experimental.sessionReplay.errorSampleRate)

        if (!sampled) {
            options.logger.log(INFO, "Replay wasn't sampled by errorSampleRate, not capturing for event")
            return
        }

        // write replayId to scope right away, so it gets picked up by the event that caused buffer
        // to flush
        hub?.configureScope {
            it.replayId = currentReplayId
        }

        if (isTerminating) {
            this.isTerminating.set(true)
            // avoid capturing replay, because the video will be malformed
            options.logger.log(DEBUG, "Not capturing replay for crashed event, will be captured on next launch")
            return
        }

        createCurrentSegment("capture_replay") { segment ->
            bufferedSegments.capture()

            if (segment is ReplaySegment.Created) {
                segment.capture(hub)

                // we only want to increment segment_id in the case of success, but currentSegment
                // might be irrelevant since we changed strategies, so in the callback we increment
                // it on the new strategy already
                // TODO: also pass new segmentTimestamp to the new strategy
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
            bufferedSegments.rotate(bufferLimit)
        }
    }

    override fun onConfigurationChanged(recorderConfig: ScreenshotRecorderConfig) {
        createCurrentSegment("configuration_changed") { segment ->
            if (segment is ReplaySegment.Created) {
                bufferedSegments += segment

                currentSegment++
            }
        }
        super.onConfigurationChanged(recorderConfig)
    }

    override fun convert(): CaptureStrategy {
        if (isTerminating.get()) {
            options.logger.log(DEBUG, "Not converting to session mode, because the process is about to terminate")
            return this
        }
        // we hand over replayExecutor to the new strategy to preserve order of execution
        val captureStrategy = SessionCaptureStrategy(options, hub, dateProvider, replayExecutor)
        captureStrategy.start(recorderConfig, segmentId = currentSegment, replayId = currentReplayId)
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
            bufferedScreens.clear()
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

    private fun MutableList<ReplaySegment.Created>.capture() {
        var bufferedSegment = removeFirstOrNull()
        while (bufferedSegment != null) {
            bufferedSegment.capture(hub)
            bufferedSegment = removeFirstOrNull()
            // a short delay between processing envelopes to avoid bursting our server and hitting
            // another rate limit https://develop.sentry.dev/sdk/features/#additional-capabilities
            // InterruptedException will be handled by the outer try-catch
            Thread.sleep(ENVELOPE_PROCESSING_DELAY)
        }
    }

    private fun MutableList<ReplaySegment.Created>.rotate(bufferLimit: Long) {
        // TODO: can be a single while-loop
        var removed = false
        removeAll {
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
            forEachIndexed { index, segment ->
                segment.setSegmentId(index)
            }
        }
    }

    private fun createCurrentSegment(taskName: String, onSegmentCreated: (ReplaySegment) -> Unit) {
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

        findAndSetStartScreen(currentSegmentTimestamp.time)

        replayExecutor.submitSafely(options, "$TAG.$taskName") {
            val segment =
                createSegmentInternal(duration, currentSegmentTimestamp, replayId, segmentId, height, width, BUFFER)
            onSegmentCreated(segment)
        }
    }
}
