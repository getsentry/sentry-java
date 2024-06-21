package io.sentry.android.replay.capture

import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.util.submitSafely
import io.sentry.protocol.SentryId
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.FileUtils
import java.util.concurrent.ScheduledExecutorService

internal class SessionCaptureStrategy(
    private val options: SentryOptions,
    private val hub: IHub?,
    private val dateProvider: ICurrentDateProvider,
    recorderConfig: ScreenshotRecorderConfig,
    executor: ScheduledExecutorService? = null,
    replayCacheProvider: ((replayId: SentryId) -> ReplayCache)? = null
) : BaseCaptureStrategy(options, hub, dateProvider, recorderConfig, executor, replayCacheProvider) {

    internal companion object {
        private const val TAG = "SessionCaptureStrategy"
    }

    override fun start(segmentId: Int, replayId: SentryId, cleanupOldReplays: Boolean) {
        super.start(segmentId, replayId, cleanupOldReplays)
        // only set replayId on the scope if it's a full session, otherwise all events will be
        // tagged with the replay that might never be sent when we're recording in buffer mode
        hub?.configureScope {
            it.replayId = currentReplayId.get()
            screenAtStart.set(it.screen)
        }
    }

    override fun pause() {
        createCurrentSegment("pause") { segment ->
            if (segment is ReplaySegment.Created) {
                segment.capture(hub)

                currentSegment.getAndIncrement()
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

    override fun sendReplayForEvent(isCrashed: Boolean, eventId: String?, hint: Hint?, onSegmentSent: () -> Unit) {
        if (!isCrashed) {
            options.logger.log(DEBUG, "Replay is already running in 'session' mode, not capturing for event %s", eventId)
        } else {
            options.logger.log(DEBUG, "Replay is already running in 'session' mode, capturing last segment for crashed event %s", eventId)
            createCurrentSegment("send_replay_for_event") { segment ->
                if (segment is ReplaySegment.Created) {
                    segment.capture(hub, hint ?: Hint())
                }
            }
        }
    }

    override fun onScreenshotRecorded(store: ReplayCache.(frameTimestamp: Long) -> Unit) {
        // have to do it before submitting, otherwise if the queue is busy, the timestamp won't be
        // reflecting the exact time of when it was captured
        val frameTimestamp = dateProvider.currentTimeMillis
        val height = recorderConfig.recordingHeight
        val width = recorderConfig.recordingWidth
        replayExecutor.submitSafely(options, "$TAG.add_frame") {
            cache?.store(frameTimestamp)

            val now = dateProvider.currentTimeMillis
            if ((now - segmentTimestamp.get().time >= options.experimental.sessionReplay.sessionSegmentDuration)) {
                val currentSegmentTimestamp = segmentTimestamp.get()
                val segmentId = currentSegment.get()
                val replayId = currentReplayId.get()

                val segment =
                    createSegment(
                        options.experimental.sessionReplay.sessionSegmentDuration,
                        currentSegmentTimestamp,
                        replayId,
                        segmentId,
                        height,
                        width
                    )
                if (segment is ReplaySegment.Created) {
                    segment.capture(hub)
                    currentSegment.getAndIncrement()
                    // set next segment timestamp as close to the previous one as possible to avoid gaps
                    segmentTimestamp.set(DateUtils.getDateTime(currentSegmentTimestamp.time + segment.videoDuration))
                }
            } else if ((now - replayStartTimestamp.get() >= options.experimental.sessionReplay.sessionDuration)) {
                stop()
                options.logger.log(INFO, "Session replay deadline exceeded (1h), stopping recording")
            }
        }
    }

    override fun onConfigurationChanged(recorderConfig: ScreenshotRecorderConfig) {
        val currentSegmentTimestamp = segmentTimestamp.get()
        createCurrentSegment("onConfigurationChanged") { segment ->
            if (segment is ReplaySegment.Created) {
                segment.capture(hub)

                currentSegment.getAndIncrement()
                // set next segment timestamp as close to the previous one as possible to avoid gaps
                segmentTimestamp.set(DateUtils.getDateTime(currentSegmentTimestamp.time + segment.videoDuration))
            }
        }

        // refresh recorder config after submitting the last segment with current config
        super.onConfigurationChanged(recorderConfig)
    }

    override fun convert(): CaptureStrategy = this

    private fun createCurrentSegment(taskName: String, onSegmentCreated: (ReplaySegment) -> Unit) {
        val now = dateProvider.currentTimeMillis
        val currentSegmentTimestamp = segmentTimestamp.get()
        val segmentId = currentSegment.get()
        val duration = now - (currentSegmentTimestamp?.time ?: 0)
        val replayId = currentReplayId.get()
        val height = recorderConfig.recordingHeight
        val width = recorderConfig.recordingWidth
        replayExecutor.submitSafely(options, "$TAG.$taskName") {
            val segment =
                createSegment(duration, currentSegmentTimestamp, replayId, segmentId, height, width)
            onSegmentCreated(segment)
        }
    }
}
