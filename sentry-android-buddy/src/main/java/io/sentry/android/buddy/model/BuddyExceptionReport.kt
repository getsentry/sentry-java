package io.sentry.android.buddy.model

/** A captured exception, flattened for its live feed item. */
internal data class BuddyExceptionReport(
  val id: Long,
  val type: String,
  val value: String?,
  val frames: List<BuddyExceptionFrame>,
)

/** One stack frame the live feed can show and open in the host IDE. */
internal data class BuddyExceptionFrame(
  val function: String?,
  val module: String?,
  val filename: String?,
  val lineno: Int?,
  val inApp: Boolean,
) {
  /** A short one-line label, e.g. `MainActivity.onCreate (MainActivity.kt:42)`. */
  val display: String
    get() = buildString {
      append(module?.substringAfterLast('.') ?: filename?.substringBeforeLast('.') ?: "<unknown>")
      function?.let { append('.').append(it) }
      val location = filename?.let { name -> lineno?.let { "$name:$it" } ?: name }
      location?.let { append(" (").append(it).append(')') }
    }
}
