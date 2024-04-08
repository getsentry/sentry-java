package io.sentry.android.replay.capture

import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.SentryEvent
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent.ReplayType.BUFFER
import io.sentry.android.replay.util.sample
import io.sentry.android.replay.util.submitSafely
import io.sentry.protocol.SentryId
import io.sentry.transport.ICurrentDateProvider
import java.security.SecureRandom
import java.util.concurrent.TimeUnit.MILLISECONDS

class BufferCaptureStrategy(
    private val options: SentryOptions,
    private val hub: IHub?,
    private val dateProvider: ICurrentDateProvider,
    private val random: SecureRandom
) : BaseCaptureStrategy(options, dateProvider) {

    internal companion object {
        private const val TAG = "BufferCaptureStrategy"
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun pause() {
        TODO("Not yet implemented")
    }

    override fun resume() {
        TODO("Not yet implemented")
    }

    override fun isRecording(): Boolean {
        TODO("Not yet implemented")
    }

    override fun sendReplayForEvent(event: SentryEvent, hint: Hint) {
        val sampled = random.sample(options.experimental.sessionReplay.errorSampleRate)

        if (sampled) {
            // don't ask me why
            event.setTag("replayId", currentReplayId.get().toString())
        } else {
            options.logger.log(INFO, "Replay wasn't sampled by errorSampleRate, not capturing for event %s", event.eventId)
            return
        }

        val errorReplayDuration = options.experimental.sessionReplay.errorReplayDuration
        val now = dateProvider.currentTimeMillis
        val currentSegmentTimestamp = if (cache?.frames?.isNotEmpty() == true) {
            // in buffer mode we have to set the timestamp of the first frame as the actual start
            DateUtils.getDateTime(cache!!.frames.first().timestamp)
        } else {
            DateUtils.getDateTime(now - errorReplayDuration)
        }
        val segmentId = currentSegment.get()
        val replayId = currentReplayId.get()
        val height = recorderConfig.recordingHeight
        val width = recorderConfig.recordingWidth
        try {
            replayExecutor.submitSafely(options, "$TAG.send_replay_for_event") {
                val videoDuration =
                    createAndCaptureSegment(
                        now - currentSegmentTimestamp.time,
                        currentSegmentTimestamp,
                        replayId,
                        segmentId,
                        height,
                        width,
                        BUFFER,
                        hint
                    )
                if (videoDuration != null) {
                    currentSegment.getAndIncrement()
                }
            }?.get(options.flushTimeoutMillis, MILLISECONDS)
        } catch (e: Throwable) {
            options.logger.log(INFO, "$TAG.send_replay_for_event future failed", e)
        }
    }

    override fun onConfigurationChanged() {
        TODO("Not yet implemented")
    }

    override fun convert(): CaptureStrategy {
        cache?.close()
        val captureStrategy = SessionCaptureStrategy(options, hub, dateProvider)
        captureStrategy.start(segmentId = currentSegment.get(), replayId = currentReplayId.get(), cleanupOldReplays = false)
        return captureStrategy
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
