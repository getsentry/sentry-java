package io.sentry.android.replay.util

/**
 * An abstraction over [android.text.Layout] with different implementations for Views and Compose.
 */
internal interface TextLayout {
  val lineCount: Int

  /**
   * Returns the dominant text color of the layout by looking at the [ForegroundColorSpan] spans if
   * this text is a [Spanned] text. If the text is not a [Spanned] text or there are no spans, it
   * returns null.
   */
  val dominantTextColor: Int?

  fun getPrimaryHorizontal(line: Int, offset: Int): Float

  fun getEllipsisCount(line: Int): Int

  fun getLineVisibleEnd(line: Int): Int

  fun getLineTop(line: Int): Int

  fun getLineBottom(line: Int): Int

  fun getLineStart(line: Int): Int
}
