package io.sentry.time

/** An [EpochClock] whose instant only moves when a test moves it. */
internal class FixedEpochClock(var epochNanos: Long = 0) : EpochClock {
  override fun now(): Timestamp = Timestamp.ofEpochNanos(epochNanos)
}
