package io.sentry.compose.navigation3.internal.backstack

/**
 * Host app text that reports [length], which may differ from [text]'s, and counts its conversions
 * to a `String` in [toStringCalls].
 */
internal class CountingText(override val length: Int, private val text: String) : CharSequence {
  var toStringCalls = 0
    private set

  override fun get(index: Int): Char = text[index]

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
    text.subSequence(startIndex, endIndex)

  override fun toString(): String {
    toStringCalls++
    return text
  }
}

/** Host app text whose conversion to a `String` throws [error]. */
internal class FailingText(val error: RuntimeException) : CharSequence {
  override val length: Int = 1

  override fun get(index: Int): Char = 'x'

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = "x"

  @Suppress("ExceptionRaisedInUnexpectedLocation") override fun toString(): String = throw error
}
