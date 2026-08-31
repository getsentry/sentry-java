package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** {@link UptimeClock} backed by {@link System#nanoTime()}. */
@ApiStatus.Internal
public final class JavaUptimeClock implements UptimeClock {

  private static final JavaUptimeClock instance = new JavaUptimeClock();

  public static @NotNull UptimeClock getInstance() {
    return instance;
  }

  private JavaUptimeClock() {}

  @Override
  public long tickNanos() {
    return System.nanoTime();
  }
}
