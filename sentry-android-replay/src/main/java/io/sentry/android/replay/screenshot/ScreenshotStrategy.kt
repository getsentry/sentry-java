package io.sentry.android.replay.screenshot

import android.view.View

internal interface ScreenshotStrategy {
  fun capture(root: View)

  fun onContentChanged()

  fun close()

  fun lastCaptureSuccessful(): Boolean

  fun emitLastScreenshot()

  /**
   * Whether the last capture detected SurfaceViews that render independently of the View tree. When
   * true, the recorder bypasses the contentChanged guard since SurfaceView redraws don't trigger
   * ViewTreeObserver.OnDrawListener.
   */
  fun hasSurfaceViews(): Boolean = false
}
