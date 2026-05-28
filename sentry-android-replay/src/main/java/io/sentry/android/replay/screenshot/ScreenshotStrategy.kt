package io.sentry.android.replay.screenshot

import android.view.View

internal interface ScreenshotStrategy {
  fun capture(root: View)

  fun onContentChanged()

  fun close()

  fun lastCaptureSuccessful(): Boolean

  fun emitLastScreenshot()
}
