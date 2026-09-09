package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** {@link MonotonicTicker} backed by {@link System#nanoTime()}. */
@ApiStatus.Internal
public final class JavaMonotonicTicker implements MonotonicTicker {

  private static final JavaMonotonicTicker instance = new JavaMonotonicTicker();

  public static @NotNull MonotonicTicker getInstance() {
    return instance;
  }

  private JavaMonotonicTicker() {}

  @Override
  public long tickNanos() {
    return System.nanoTime();
  }
}
