package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * One wall-clock reading plus a monotonic ticker, producing timestamps that are safe to subtract
 * from each other.
 *
 * <p>Wall clocks jump. Read the wall clock twice and the device may have changed it in between, so
 * the gap between the two readings is not the time that actually passed — it can come out too
 * short, too long, or negative. That is how a child span ends up starting before its parent.
 *
 * <p>So for a group of timestamps that will be compared with each other — the spans of a
 * transaction, the samples of a profile chunk, the frames of a replay segment — read the wall clock
 * only once (the anchor) and build the rest by adding the time a {@link MonotonicTicker} has
 * measured since, which only ever moves forward. Gaps between those timestamps are then real
 * elapsed time. Spans need exactly that: the protocol sends a start and an end timestamp and no
 * duration, so the server subtracts them.
 */
@ApiStatus.Internal
public final class AnchoredClock {

  private final @NotNull MonotonicTicker ticker;
  private final long epochNanos;
  private final long anchorTick;

  private AnchoredClock(
      final @NotNull MonotonicTicker ticker, final long epochNanos, final long anchorTick) {
    this.ticker = ticker;
    this.epochNanos = epochNanos;
    this.anchorTick = anchorTick;
  }

  /** Takes the anchor now: one wall-clock reading and one tick, back to back. */
  public static @NotNull AnchoredClock create(
      final @NotNull EpochClock epoch, final @NotNull MonotonicTicker ticker) {
    return new AnchoredClock(ticker, epoch.now().epochNanos(), ticker.tickNanos());
  }

  /**
   * The anchor itself: the one timestamp here that was read from the wall clock rather than built
   * from a tick. Reads no clock and never changes.
   */
  public @NotNull Timestamp startTime() {
    return Timestamp.anchoredAt(epochNanos, this);
  }

  /** Now: {@link #startTime()} plus the time the ticker has measured since the anchor. */
  public @NotNull Timestamp now() {
    return at(ticker.tickNanos());
  }

  /**
   * The instant a tick corresponds to, for placing something already measured on this ticker — a
   * frame, a profiler sample — on the same timeline as the instants projected here.
   */
  public @NotNull Timestamp at(final long tickNanos) {
    return Timestamp.anchoredAt(epochNanos + (tickNanos - anchorTick), this);
  }

  /**
   * The tick an instant was projected from. Exact, and reads no clock: projection adds a tick
   * difference to a fixed epoch, so subtraction inverts it.
   *
   * @throws IllegalArgumentException if this clock did not project the instant. Its epoch bears no
   *     arithmetic relation to these ticks, so converting it would silently produce a tick derived
   *     from a wall-clock difference.
   */
  public long tickOf(final @NotNull Timestamp timestamp) {
    if (timestamp.anchor() != this) {
      throw new IllegalArgumentException(
          "Timestamp was not projected by this AnchoredClock: " + timestamp);
    }
    return anchorTick + (timestamp.epochNanos() - epochNanos);
  }
}
