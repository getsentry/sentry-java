package io.sentry.android.replay

import android.graphics.Bitmap
import io.sentry.SentryReplayOptions
import io.sentry.TypeCheckHint

// since we don't have getters for maskAllText and maskAllimages, they won't be accessible as
// properties in Kotlin, therefore we create these extensions where a getter is dummy, but a setter
// delegates to the corresponding method in SentryReplayOptions

/**
 * Mask all text content. Draws a rectangle of text bounds with text color on top. By default only
 * views extending TextView are masked.
 *
 * <p>Default is enabled.
 */
public var SentryReplayOptions.maskAllText: Boolean
  @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
  get() = error("Getter not supported")
  set(value) = setMaskAllText(value)

/**
 * Mask all image content. Draws a rectangle of image bounds with image's dominant color on top. By
 * default only views extending ImageView with BitmapDrawable or custom Drawable type are masked.
 * ColorDrawable, InsetDrawable, VectorDrawable are all considered non-PII, as they come from the
 * apk.
 *
 * <p>Default is enabled.
 */
public var SentryReplayOptions.maskAllImages: Boolean
  @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
  get() = error("Getter not supported")
  set(value) = setMaskAllImages(value)

/**
 * Sets a callback that is invoked right before a replay frame is stored to disk. The callback
 * receives the frame bitmap (with masking applied), the timestamp, and the current screen name.
 *
 * The callback runs on a background thread (the replay executor). Do not recycle the bitmap — it
 * may be reused by the replay system.
 *
 * @param callback the callback to invoke, or null to clear
 */
public fun SentryReplayOptions.beforeStoreFrame(
  callback: ((frameBitmap: Bitmap, frameTimestamp: Long, screenName: String?) -> Unit)?
) {
  beforeStoreFrame =
    if (callback != null) {
      SentryReplayOptions.BeforeStoreFrameCallback { hint, timestamp, screen ->
        val bitmap = hint.getAs(TypeCheckHint.REPLAY_FRAME_BITMAP, Bitmap::class.java)
        if (bitmap != null) {
          callback(bitmap, timestamp, screen)
        }
      }
    } else {
      null
    }
}
