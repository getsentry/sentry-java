package io.sentry.android.replay.capture

import android.graphics.Bitmap
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.protocol.SentryId
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal interface CaptureStrategy {
    val currentSegment: AtomicInteger
    val currentReplayId: AtomicReference<SentryId>

    fun start(segmentId: Int = 0, replayId: SentryId = SentryId(), cleanupOldReplays: Boolean = true)

    fun stop()

    fun pause()

    fun resume()

    fun sendReplayForEvent(event: SentryEvent, hint: Hint, onSegmentSent: () -> Unit)

    fun onScreenshotRecorded(bitmap: Bitmap)

    fun onConfigurationChanged(recorderConfig: ScreenshotRecorderConfig)

    fun convert(): CaptureStrategy

    fun close()
}
