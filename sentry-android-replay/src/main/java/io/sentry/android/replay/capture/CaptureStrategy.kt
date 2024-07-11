package io.sentry.android.replay.capture

import android.view.MotionEvent
import io.sentry.Hint
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.protocol.SentryId
import java.io.File

internal interface CaptureStrategy {
    var currentSegment: Int
    var currentReplayId: SentryId
    val replayCacheDir: File?

    fun start(recorderConfig: ScreenshotRecorderConfig, segmentId: Int = 0, replayId: SentryId = SentryId(), cleanupOldReplays: Boolean = true)

    fun stop()

    fun pause()

    fun resume()

    fun sendReplayForEvent(isCrashed: Boolean, eventId: String?, hint: Hint?, onSegmentSent: () -> Unit)

    fun onScreenshotRecorded(store: ReplayCache.(frameTimestamp: Long) -> Unit)

    fun onConfigurationChanged(recorderConfig: ScreenshotRecorderConfig)

    fun onTouchEvent(event: MotionEvent)

    fun onScreenChanged(screen: String?) = Unit

    fun convert(): CaptureStrategy

    fun close()
}
