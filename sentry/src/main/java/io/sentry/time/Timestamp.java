package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An instant on the wall clock, as nanoseconds since the Unix epoch.
 *
 * <p>Unlike a {@link MonotonicClock} tick, a timestamp means something outside this process: it can
 * be serialized, stored, and compared against a value from another machine.
 *
 * <p>It deliberately offers no arithmetic between instants. Subtracting two independent wall-clock
 * readings gives a duration the device's clock can lengthen, shorten or make negative. Durations
 * come from a {@link Stopwatch}, or from two instants an {@link AnchoredClock} projected from the
 * same tick.
 *
 * <p>{@link #anchor()} records which of those this is. An instant read straight from the wall
 * clock, or stated by something outside this process, has no anchor and can only be serialized. One
 * an {@link AnchoredClock} produced references that clock, which lets {@link AnchoredClock#tickOf}
 * recover the tick it came from and reject instants it did not produce.
 *
 * <p>Nanoseconds since the epoch overflow a long in the year 2262.
 */
@ApiStatus.Internal
public final class Timestamp {

  private final long epochNanos;
  private final @Nullable AnchoredClock anchor;

  private Timestamp(final long epochNanos, final @Nullable AnchoredClock anchor) {
    this.epochNanos = epochNanos;
    this.anchor = anchor;
  }

  /** An instant read straight from a wall clock, or stated by something outside this process. */
  public static @NotNull Timestamp ofEpochNanos(final long epochNanos) {
    return new Timestamp(epochNanos, null);
  }

  static @NotNull Timestamp anchoredAt(final long epochNanos, final @NotNull AnchoredClock anchor) {
    return new Timestamp(epochNanos, anchor);
  }

  public long epochNanos() {
    return epochNanos;
  }

  /** The clock that projected this instant, or null if it was read or stated directly. */
  @Nullable
  AnchoredClock anchor() {
    return anchor;
  }

  /**
   * Equality is by instant. The anchor records how the instant was obtained, not what it denotes,
   * so two readings of the same moment are equal whether or not they were projected.
   */
  @Override
  public boolean equals(final @Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Timestamp)) {
      return false;
    }
    return epochNanos == ((Timestamp) other).epochNanos;
  }

  @Override
  public int hashCode() {
    return (int) (epochNanos ^ (epochNanos >>> 32));
  }

  @Override
  public @NotNull String toString() {
    return "Timestamp{epochNanos=" + epochNanos + '}';
  }
}
