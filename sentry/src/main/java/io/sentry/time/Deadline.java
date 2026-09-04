package io.sentry.time;

import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A point in the future, measured on a {@link MonotonicClock}.
 *
 * <p>Exists so that callers never do arithmetic on raw ticks. A tick carries no unit and no epoch,
 * so spelling out {@code now - then < ttl} at every call site is where unit mix-ups, sentinels that
 * happen to mean "boot", and wrap-unsafe {@code <} comparisons come from. Each of those is decided
 * once, here.
 */
@ApiStatus.Internal
public final class Deadline {

  private final @NotNull MonotonicClock clock;
  private final long deadlineNanos;

  private Deadline(final @NotNull MonotonicClock clock, final long deadlineNanos) {
    this.clock = clock;
    this.deadlineNanos = deadlineNanos;
  }

  /** A deadline {@code amount} of {@code unit} from now. */
  public static @NotNull Deadline after(
      final @NotNull MonotonicClock clock, final long amount, final @NotNull TimeUnit unit) {
    return new Deadline(clock, clock.tickNanos() + unit.toNanos(amount));
  }

  /**
   * A deadline that has already passed. Use for state that has not been populated yet, so that
   * "never set" needs no numeric sentinel and cannot be mistaken for fresh — {@code 0} is a real
   * and very recent instant on any boot-relative clock.
   */
  public static @NotNull Deadline passed(final @NotNull MonotonicClock clock) {
    return new Deadline(clock, clock.tickNanos());
  }

  public boolean hasPassed() {
    // Subtraction rather than `<`: a tick origin is arbitrary, may be negative, and may wrap.
    return clock.tickNanos() - deadlineNanos >= 0;
  }

  /**
   * How much time is left, rounded up, or zero once the deadline has passed.
   *
   * <p>Rounding up matters: callers schedule work for {@code remaining()} and then re-check {@link
   * #hasPassed()}. Truncating would wake them a fraction early, to find the deadline still
   * standing.
   */
  public long remaining(final @NotNull TimeUnit unit) {
    final long remainingNanos = deadlineNanos - clock.tickNanos();
    if (remainingNanos <= 0) {
      return 0;
    }
    final long unitNanos = unit.toNanos(1);
    final long whole = remainingNanos / unitNanos;
    return remainingNanos % unitNanos == 0 ? whole : whole + 1;
  }

  /**
   * Whether this deadline falls after {@code other}.
   *
   * @throws IllegalArgumentException if the two were created from different clocks, whose origins
   *     are unrelated and whose ticks are therefore not comparable.
   */
  public boolean isAfter(final @NotNull Deadline other) {
    if (clock != other.clock) {
      throw new IllegalArgumentException(
          "Cannot compare deadlines from different clocks: "
              + clock.getClass().getName()
              + " and "
              + other.clock.getClass().getName());
    }
    return deadlineNanos - other.deadlineNanos > 0;
  }
}
