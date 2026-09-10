package io.sentry.time;

import io.sentry.SentryDate;
import io.sentry.SentryLongDate;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An instant on the wall clock, as nanoseconds since the Unix epoch.
 *
 * <p>Unlike a {@link MonotonicTicker} tick, a timestamp means something outside this process: it
 * can be serialized, stored, and compared against a value from another machine.
 *
 * <p>It deliberately offers no arithmetic between instants. Subtracting two independent wall-clock
 * readings gives a duration the device's clock can lengthen, shorten or make negative. Durations
 * come from a {@link Stopwatch}, or from two instants an {@link AnchoredClock} projected from the
 * same tick.
 *
 * <p>Nanoseconds since the epoch overflow a long in the year 2262.
 */
@ApiStatus.Internal
public final class Timestamp {

  private final long epochNanos;

  private Timestamp(final long epochNanos) {
    this.epochNanos = epochNanos;
  }

  public static @NotNull Timestamp ofEpochNanos(final long epochNanos) {
    return new Timestamp(epochNanos);
  }

  public long epochNanos() {
    return epochNanos;
  }

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
    return Long.hashCode(epochNanos);
  }

  @Override
  public @NotNull String toString() {
    return "Timestamp{epochNanos=" + epochNanos + '}';
  }

  /**
   * This forms an easy bridge between our old API and the new API.
   *
   * @return a SentryDate implemented by a SentryLongDate
   */
  public SentryDate getSentryDate() {
    return new SentryLongDate(epochNanos);
  }
}
