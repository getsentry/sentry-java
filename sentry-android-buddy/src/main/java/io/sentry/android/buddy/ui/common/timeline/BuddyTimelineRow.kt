package io.sentry.android.buddy.ui.common.timeline

/** One line of a [BuddyTimeline], already reduced to display strings. */
internal data class BuddyTimelineRow(
  val id: Long,
  /** Left-hand stamp, already formatted - elapsed time in a recording, age in the live feed. */
  val stamp: String,
  val detail: String,
  val trailing: String? = null,
  val tone: BuddyTimelineTone = BuddyTimelineTone.NEUTRAL,
  val emphasized: Boolean = false,
  val link: String? = null,
)

/** Drives the row colour. The timeline never reads severity or span types itself. */
internal enum class BuddyTimelineTone {
  NEUTRAL,
  ACCENT,
  WARNING,
  ERROR,
}
