package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** {@link MonotonicTicker} backed by {@link System#nanoTime()}. */
@ApiStatus.Internal
public final class JavaMonotonicTicker implements MonotonicTicker {

  private static final JavaMonotonicTicker instance = new JavaMonotonicTicker();

  /**
   * Prefer {@link io.sentry.SentryOptions#getMonotonicTicker()}, which picks the platform's ticker.
   * On Android this one stops counting during device suspend, so intervals spanning a deep sleep
   * come out short.
   */
  public static @NotNull MonotonicTicker getInstance() {
    return instance;
  }

  private JavaMonotonicTicker() {}

  @Override
  public long tickNanos() {
    return System.nanoTime();
  }
}
