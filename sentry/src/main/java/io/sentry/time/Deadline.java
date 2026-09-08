package io.sentry.time;

import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A point in the future, measured on a {@link MonotonicTicker}.
 *
 * <p>Exists so that callers never do arithmetic on raw ticks. A tick carries no unit and no epoch,
 * so spelling out {@code now - then < ttl} at every call site is where unit mix-ups, sentinels that
 * happen to mean "boot", and wrap-unsafe {@code <} comparisons come from. Each of those is decided
 * once, here.
 */
@ApiStatus.Internal
public final class Deadline {

  private final @NotNull MonotonicTicker ticker;
  private final long deadlineNanos;

  private Deadline(final @NotNull MonotonicTicker ticker, final long deadlineNanos) {
    this.ticker = ticker;
    this.deadlineNanos = deadlineNanos;
  }

  /**
   * A deadline {@code amount} of {@code unit} from now.
   *
   * @throws IllegalArgumentException if {@code amount} is negative. A deadline that starts out in
   *     the past is a sign error at the call site; {@link #passed} says it deliberately.
   */
  public static @NotNull Deadline after(
      final @NotNull MonotonicTicker ticker, final long amount, final @NotNull TimeUnit unit) {
    if (amount < 0) {
      throw new IllegalArgumentException("Deadline amount must not be negative, but was " + amount);
    }
    return new Deadline(ticker, ticker.tickNanos() + unit.toNanos(amount));
  }

  /**
   * A deadline that has already passed.
   *
   * <p>Saves callers from reserving a tick value to mean "not set yet": {@code 0} is a real and
   * very recent instant on a boot-relative ticker, so a field left at {@code 0} reads as freshly
   * set rather than as unset.
   */
  public static @NotNull Deadline passed(final @NotNull MonotonicTicker ticker) {
    return new Deadline(ticker, ticker.tickNanos());
  }

  public boolean hasPassed() {
    // Subtraction rather than `<`: a tick origin is arbitrary, may be negative, and may wrap.
    return ticker.tickNanos() - deadlineNanos >= 0;
  }

  /**
   * How much time is left, rounded up, or zero once the deadline has passed.
   *
   * <p>Rounding up matters: callers schedule work for {@code remaining()} and then re-check {@link
   * #hasPassed()}. Truncating would wake them a fraction early, to find the deadline still
   * standing.
   */
  public long remaining(final @NotNull TimeUnit unit) {
    final long remainingNanos = deadlineNanos - ticker.tickNanos();
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
   * @throws IllegalArgumentException if the two were created from different tickers, whose origins
   *     are unrelated and whose ticks are therefore not comparable.
   */
  public boolean isAfter(final @NotNull Deadline other) {
    if (ticker != other.ticker) {
      throw new IllegalArgumentException(
          "Cannot compare deadlines from different tickers: "
              + ticker.getClass().getName()
              + " and "
              + other.ticker.getClass().getName());
    }
    return deadlineNanos - other.deadlineNanos > 0;
  }
}
