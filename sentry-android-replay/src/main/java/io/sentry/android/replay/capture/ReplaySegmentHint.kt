package io.sentry.android.replay.capture

import io.sentry.hints.DiscardNotification
import java.io.File

/**
 * Hint for a captured replay segment. The send path owns the segment video: it deletes the video
 * after reading it, and this hint deletes it when the envelope is dropped before that.
 */
internal open class ReplaySegmentHint(private val videoFile: File?) : DiscardNotification {
  override fun markDiscarded() {
    videoFile?.delete()
  }
}
