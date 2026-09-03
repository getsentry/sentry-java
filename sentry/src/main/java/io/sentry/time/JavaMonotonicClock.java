package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** {@link MonotonicClock} backed by {@link System#nanoTime()}. */
@ApiStatus.Internal
public final class JavaMonotonicClock implements MonotonicClock {

  private static final JavaMonotonicClock instance = new JavaMonotonicClock();

  public static @NotNull MonotonicClock getInstance() {
    return instance;
  }

  private JavaMonotonicClock() {}

  @Override
  public long tickNanos() {
    return System.nanoTime();
  }
}
