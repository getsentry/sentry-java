package io.sentry.android.replay.capture

import android.graphics.Bitmap
import io.sentry.DateUtils
import io.sentry.IConnectionStatusProvider.ConnectionStatus.DISCONNECTED
import io.sentry.IHub
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.capture.CaptureStrategy.ReplaySegment
import io.sentry.android.replay.util.submitSafely
import io.sentry.protocol.SentryId
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.FileUtils
import java.util.concurrent.ScheduledExecutorService

internal class SessionCaptureStrategy(
    private val options: SentryOptions,
    private val hub: IHub?,
    private val dateProvider: ICurrentDateProvider,
    executor: ScheduledExecutorService? = null,
    replayCacheProvider: ((replayId: SentryId, recorderConfig: ScreenshotRecorderConfig) -> ReplayCache)? = null
) : BaseCaptureStrategy(options, hub, dateProvider, executor, replayCacheProvider) {

    internal companion object {
        private const val TAG = "SessionCaptureStrategy"
    }

    override fun start(
        recorderConfig: ScreenshotRecorderConfig,
        segmentId: Int,
        replayId: SentryId
    ) {
        super.start(recorderConfig, segmentId, replayId)
        // only set replayId on the scope if it's a full session, otherwise all events will be
        // tagged with the replay that might never be sent when we're recording in buffer mode
        hub?.configureScope {
            it.replayId = currentReplayId
            screenAtStart = it.screen
        }
    }

    override fun pause() {
        createCurrentSegment("pause") { segment ->
            if (segment is ReplaySegment.Created) {
                segment.capture(hub)

                currentSegment++
            }
        }
        super.pause()
    }

    override fun stop() {
        val replayCacheDir = cache?.replayCacheDir
        createCurrentSegment("stop") { segment ->
            if (segment is ReplaySegment.Created) {
                segment.capture(hub)
            }
            FileUtils.deleteRecursively(replayCacheDir)
        }
        hub?.configureScope { it.replayId = SentryId.EMPTY_ID }
        super.stop()
    }

    override fun captureReplay(isTerminating: Boolean, onSegmentSent: () -> Unit) {
        options.logger.log(DEBUG, "Replay is already running in 'session' mode, not capturing for event")
        this.isTerminating.set(isTerminating)
    }

    override fun onScreenshotRecorded(bitmap: Bitmap?, store: ReplayCache.(frameTimestamp: Long) -> Unit) {
        if (options.connectionStatusProvider.connectionStatus == DISCONNECTED) {
            options.logger.log(DEBUG, "Skipping screenshot recording, no internet connection")
            bitmap?.recycle()
            return
        }
        // have to do it before submitting, otherwise if the queue is busy, the timestamp won't be
        // reflecting the exact time of when it was captured
        val frameTimestamp = dateProvider.currentTimeMillis
        val height = recorderConfig.recordingHeight
        val width = recorderConfig.recordingWidth
        replayExecutor.submitSafely(options, "$TAG.add_frame") {
            cache?.store(frameTimestamp)

            val currentSegmentTimestamp = segmentTimestamp
            currentSegmentTimestamp ?: run {
                options.logger.log(DEBUG, "Segment timestamp is not set, not recording frame")
                return@submitSafely
            }

            if (isTerminating.get()) {
                options.logger.log(DEBUG, "Not capturing segment, because the app is terminating, will be captured on next launch")
                return@submitSafely
            }

            val now = dateProvider.currentTimeMillis
            if ((now - currentSegmentTimestamp.time >= options.experimental.sessionReplay.sessionSegmentDuration)) {
                val segment =
                    createSegmentInternal(
                        options.experimental.sessionReplay.sessionSegmentDuration,
                        currentSegmentTimestamp,
                        currentReplayId,
                        currentSegment,
                        height,
                        width
                    )
                if (segment is ReplaySegment.Created) {
                    segment.capture(hub)
                    currentSegment++
                    // set next segment timestamp as close to the previous one as possible to avoid gaps
                    segmentTimestamp = DateUtils.getDateTime(currentSegmentTimestamp.time + segment.videoDuration)
                }
            }

            if ((now - replayStartTimestamp.get() >= options.experimental.sessionReplay.sessionDuration)) {
                options.replayController.stop()
                options.logger.log(INFO, "Session replay deadline exceeded (1h), stopping recording")
            }
        }
    }

    override fun onConfigurationChanged(recorderConfig: ScreenshotRecorderConfig) {
        val currentSegmentTimestamp = segmentTimestamp ?: return
        createCurrentSegment("onConfigurationChanged") { segment ->
            if (segment is ReplaySegment.Created) {
                segment.capture(hub)

                currentSegment++
                // set next segment timestamp as close to the previous one as possible to avoid gaps
                segmentTimestamp = DateUtils.getDateTime(currentSegmentTimestamp.time + segment.videoDuration)
            }
        }

        // refresh recorder config after submitting the last segment with current config
        super.onConfigurationChanged(recorderConfig)
    }

    override fun convert(): CaptureStrategy = this

    private fun createCurrentSegment(taskName: String, onSegmentCreated: (ReplaySegment) -> Unit) {
        val now = dateProvider.currentTimeMillis
        val currentSegmentTimestamp = segmentTimestamp ?: return
        val segmentId = currentSegment
        val duration = now - currentSegmentTimestamp.time
        val replayId = currentReplayId
        val height = recorderConfig.recordingHeight
        val width = recorderConfig.recordingWidth
        replayExecutor.submitSafely(options, "$TAG.$taskName") {
            val segment =
                createSegmentInternal(duration, currentSegmentTimestamp, replayId, segmentId, height, width)
            onSegmentCreated(segment)
        }
    }
}
