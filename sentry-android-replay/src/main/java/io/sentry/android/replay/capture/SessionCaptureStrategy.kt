package io.sentry.android.replay.capture

import io.sentry.Hint
import io.sentry.IHub
import io.sentry.SentryEvent
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryOptions
import io.sentry.android.replay.util.submitSafely
import io.sentry.protocol.SentryId
import io.sentry.transport.ICurrentDateProvider

class SessionCaptureStrategy(
    private val options: SentryOptions,
    private val hub: IHub?,
    private val dateProvider: ICurrentDateProvider
) : BaseCaptureStrategy(options, dateProvider) {

    internal companion object {
        private const val TAG = "SessionCaptureStrategy"
    }

    override fun start(segmentId: Int, replayId: SentryId, cleanupOldReplays: Boolean) {
        super.start(segmentId, replayId, cleanupOldReplays)
        // only set replayId on the scope if it's a full session, otherwise all events will be
        // tagged with the replay that might never be sent when we're recording in buffer mode
        hub?.configureScope { it.replayId = currentReplayId.get() }
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun pause() {
        val now = dateProvider.currentTimeMillis
        val currentSegmentTimestamp = segmentTimestamp.get()
        val segmentId = currentSegment.get()
        val duration = now - currentSegmentTimestamp.time
        val replayId = currentReplayId.get()
        val height = recorderConfig.recordingHeight
        val width = recorderConfig.recordingWidth
        replayExecutor.submitSafely(options, "$TAG.pause") {
            val videoDuration =
                createAndCaptureSegment(duration, currentSegmentTimestamp, replayId, segmentId, height, width)
            if (videoDuration != null) {
                currentSegment.getAndIncrement()
            }
        }
    }

    override fun resume() {
        super.resume()
    }

    override fun isRecording(): Boolean {
        TODO("Not yet implemented")
    }

    override fun sendReplayForEvent(event: SentryEvent, hint: Hint) {
        // don't ask me why
        event.setTag("replayId", currentReplayId.get().toString())
        options.logger.log(DEBUG, "Replay is already running in 'session' mode, not capturing for event %s", event.eventId)
    }

    override fun onConfigurationChanged() {
        TODO("Not yet implemented")
    }

    override fun convert(): CaptureStrategy = this

    override fun close() {
        TODO("Not yet implemented")
    }
}
