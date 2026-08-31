package io.sentry.time;

import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Measures how long something took, on a {@link Ticker}.
 *
 * <p>The counterpart to {@link Deadline}: it keeps the start tick and the unit conversion in one
 * place, so call sites stop repeating {@code System.nanoTime() - startTime}.
 */
@ApiStatus.Internal
public final class Stopwatch {

  private final @NotNull Ticker clock;
  private final long startNanos;

  private Stopwatch(final @NotNull Ticker clock) {
    this.clock = clock;
    this.startNanos = clock.tickNanos();
  }

  public static @NotNull Stopwatch started(final @NotNull Ticker clock) {
    return new Stopwatch(clock);
  }

  public long elapsedNanos() {
    return clock.tickNanos() - startNanos;
  }

  public long elapsed(final @NotNull TimeUnit unit) {
    return unit.convert(elapsedNanos(), TimeUnit.NANOSECONDS);
  }
}
