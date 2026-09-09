package io.sentry.time;

import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Measures how long something took, on a {@link MonotonicTicker}.
 *
 * <p>The counterpart to {@link Deadline}: it keeps the start tick and the unit conversion in one
 * place, so call sites stop repeating {@code System.nanoTime() - startTime}.
 */
@ApiStatus.Internal
public final class Stopwatch {

  private final @NotNull MonotonicTicker ticker;
  private final long startNanos;

  private Stopwatch(final @NotNull MonotonicTicker ticker) {
    this.ticker = ticker;
    this.startNanos = ticker.tickNanos();
  }

  public static @NotNull Stopwatch started(final @NotNull MonotonicTicker ticker) {
    return new Stopwatch(ticker);
  }

  public long elapsedNanos() {
    return ticker.tickNanos() - startNanos;
  }

  public long elapsed(final @NotNull TimeUnit unit) {
    return unit.convert(elapsedNanos(), TimeUnit.NANOSECONDS);
  }
}
