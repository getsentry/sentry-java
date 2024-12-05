package io.sentry.android.replay

import io.sentry.SentryReplayOptions

// since we don't have getters for maskAllText and maskAllimages, they won't be accessible as
// properties in Kotlin, therefore we create these extensions where a getter is dummy, but a setter
// delegates to the corresponding method in SentryReplayOptions

/**
 * Mask all text content. Draws a rectangle of text bounds with text color on top. By default
 * only views extending TextView are masked.
 *
 * <p>Default is enabled.
 */
var SentryReplayOptions.maskAllText: Boolean
    @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
    get() = error("Getter not supported")
    set(value) = setMaskAllText(value)

/**
 * Mask all image content. Draws a rectangle of image bounds with image's dominant color on top.
 * By default only views extending ImageView with BitmapDrawable or custom Drawable type are
 * masked. ColorDrawable, InsetDrawable, VectorDrawable are all considered non-PII, as they come
 * from the apk.
 *
 * <p>Default is enabled.
 */
var SentryReplayOptions.maskAllImages: Boolean
    @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
    get() = error("Getter not supported")
    set(value) = setMaskAllImages(value)
