package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * One wall-clock reading pinned to one monotonic tick, from which related instants are projected.
 *
 * <p>Exists because a group of instants that will be compared against each other — the spans of a
 * transaction, the samples of a profile chunk, the frames of a replay segment — must not each read
 * the wall clock. Two independent readings differ by whatever the device's clock did in between, so
 * a duration taken across them can shorten, lengthen or go negative, and a child can appear to
 * start before its parent. Reading the epoch once and projecting the rest through {@link
 * MonotonicClock} makes every instant in the group an image of the same tick, so subtracting any
 * two of them reports measured time.
 *
 * <p>The span protocol needs exactly that: it carries a start and an end instant and no duration
 * field, so the server subtracts them.
 *
 * <p>Projection also buys resolution the wall clock does not have. On Android the epoch is
 * millisecond-granular, so an instant read directly is truncated, whereas one projected from a tick
 * carries nanoseconds — the workaround {@link io.sentry.SentryNanotimeDate} describes, applied once
 * per group instead of between each pair of readings. OpenTelemetry's SDK anchors per local root
 * span for the same two reasons.
 *
 * <p>The cost is that a projection drifts as the anchor ages: it reports what the clock said when
 * the anchor was taken, plus measured time, so a clock step afterwards is invisible to it. Anchor
 * something short-lived, and use {@link #driftNanos()} to observe the gap.
 */
@ApiStatus.Internal
public final class AnchoredClock {

  private final @NotNull EpochClock epoch;
  private final @NotNull MonotonicClock clock;
  private final long epochNanos;
  private final long anchorTick;

  private AnchoredClock(
      final @NotNull EpochClock epoch,
      final @NotNull MonotonicClock clock,
      final long epochNanos,
      final long anchorTick) {
    this.epoch = epoch;
    this.clock = clock;
    this.epochNanos = epochNanos;
    this.anchorTick = anchorTick;
  }

  /** Takes the anchor now: one epoch reading, one tick, as close together as a call allows. */
  public static @NotNull AnchoredClock create(
      final @NotNull EpochClock epoch, final @NotNull MonotonicClock clock) {
    return new AnchoredClock(epoch, clock, epoch.now().epochNanos(), clock.tickNanos());
  }

  /** The anchor itself — the one instant here that was read rather than projected. */
  public @NotNull Timestamp start() {
    return Timestamp.anchoredAt(epochNanos, this);
  }

  public @NotNull Timestamp now() {
    return at(clock.tickNanos());
  }

  /**
   * The instant a tick corresponds to, for placing something already measured on this clock — a
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

  /**
   * How far this anchor's projection has fallen behind or ahead of the wall clock, in nanoseconds.
   *
   * <p>Zero means the wall clock advanced by exactly the time this clock measured. Anything else is
   * a clock step, or — where {@link MonotonicClock} and the wall clock disagree about suspend —
   * device sleep. Reads the epoch and the tick in the same order as {@link #create}, so the gap
   * between the two reads biases the result the same way it biased the anchor.
   */
  public long driftNanos() {
    final long wallElapsed = epoch.now().epochNanos() - epochNanos;
    final long measuredElapsed = clock.tickNanos() - anchorTick;
    return wallElapsed - measuredElapsed;
  }
}
