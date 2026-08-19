package io.sentry.android.buddy.model

internal sealed class BuddyScreenScanState {
  data object Hidden : BuddyScreenScanState()

  data class Scanning(val result: BuddyScreenScanResult, val startedAtMs: Long) :
    BuddyScreenScanState()

  data class Results(val result: BuddyScreenScanResult) : BuddyScreenScanState()
}

internal data class BuddyScreenScanResult(
  val screenName: String,
  val bounds: List<BuddyScreenScanBounds>,
  val instrumentation: List<BuddyScreenInstrumentationItem>,
)

internal data class BuddyScreenScanBounds(
  val label: String,
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
) {
  val width: Float
    get() = right - left

  val height: Float
    get() = bottom - top
}

internal enum class BuddyInstrumentationStatus {
  ENABLED,
  WARNING,
  MISSING,
}

internal data class BuddyScreenInstrumentationItem(
  val label: String,
  val value: String,
  val status: BuddyInstrumentationStatus,
)
