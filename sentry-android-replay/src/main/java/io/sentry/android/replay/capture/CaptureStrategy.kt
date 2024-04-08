package io.sentry.android.replay.capture

import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.protocol.SentryId

interface CaptureStrategy {
    fun start(segmentId: Int = 0, replayId: SentryId = SentryId(), cleanupOldReplays: Boolean = true)

    fun stop()

    fun pause()

    fun resume()

    fun isRecording(): Boolean

    fun sendReplayForEvent(event: SentryEvent, hint: Hint)

    fun onConfigurationChanged()

    fun convert(): CaptureStrategy

    fun close()
}
