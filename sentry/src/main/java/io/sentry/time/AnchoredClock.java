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
    return Timestamp.ofEpochNanos(epochNanos);
  }

  /** Now: {@link #startTime()} plus the time the ticker has measured since the anchor. */
  public @NotNull Timestamp now() {
    return at(ticker.tickNanos());
  }

  private @NotNull Timestamp at(final long tickNanos) {
    return Timestamp.ofEpochNanos(epochNanos + (tickNanos - anchorTick));
  }
}
