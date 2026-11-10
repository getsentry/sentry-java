package io.sentry.android.replay.capture

import io.sentry.time.AnchoredClock
import io.sentry.time.EpochClock
import io.sentry.time.MonotonicTicker
import io.sentry.time.Timestamp
import java.util.concurrent.TimeUnit.NANOSECONDS
import kotlin.math.abs

/**
 * The timeline a recording stamps its frames, segments and touch events on.
 *
 * Those stamps are subtracted from each other: a segment is the frames within 5s of its start, and
 * the buffer is the last 30s. So they come from one [AnchoredClock], where a wall-clock change
 * can't drop frames or freeze the video. Breadcrumbs, errors and spans, though, are stamped from
 * the wall clock elsewhere in the SDK, and the player places them on the video by that stamp. So
 * the timeline must not drift away from the wall clock either: when [wallClockJumpMillis] reports a
 * jump, the recording closes the segment on the current timeline and [restart]s on the wall clock.
 *
 * Reads are safe from any thread. Checking for a jump and then restarting is a check-then-act, so
 * [BaseCaptureStrategy] does both under its timeline lock.
 */
internal class ReplayClock(
  private val epochClock: EpochClock,
  private val ticker: MonotonicTicker,
  continued: AnchoredClock? = null,
) {
  internal companion object {
    // just under the 1.1s the player shows as a gap in the video
    const val JUMP_TOLERANCE_MS = 1000L
  }

  /** The current timeline. [restart] replaces it, so a reference read once stays consistent. */
  @Volatile
  var timeline: AnchoredClock = continued ?: AnchoredClock.create(epochClock, ticker)
    private set

  fun nowMillis(): Long = timeline.nowMillis()

  /** Anchors a new timeline on the wall clock and returns its start. */
  fun restart(): Long {
    val next = AnchoredClock.create(epochClock, ticker)
    timeline = next
    return next.startTime().epochMillis
  }

  /**
   * How far the wall clock has jumped away from the timeline, positive when it moved forward, or
   * `0` while the two agree within [JUMP_TOLERANCE_MS].
   */
  fun wallClockJumpMillis(): Long {
    val jump = epochClock.now().epochMillis - nowMillis()
    return if (abs(jump) > JUMP_TOLERANCE_MS) jump else 0
  }
}

internal val Timestamp.epochMillis: Long
  get() = NANOSECONDS.toMillis(epochNanos())

internal fun AnchoredClock.nowMillis(): Long = now().epochMillis
