package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ElapsedRealtimeClock} backed by {@link System#nanoTime()}.
 *
 * <p>Identical to {@link JavaUptimeClock} — a JVM cannot observe deep sleep — but kept a distinct
 * type so that a call site declaring which guarantee it needs documents that intent on every
 * platform.
 */
@ApiStatus.Internal
public final class JavaElapsedRealtimeClock implements ElapsedRealtimeClock {

  private static final JavaElapsedRealtimeClock instance = new JavaElapsedRealtimeClock();

  public static @NotNull ElapsedRealtimeClock getInstance() {
    return instance;
  }

  private JavaElapsedRealtimeClock() {}

  @Override
  public long tickNanos() {
    return System.nanoTime();
  }
}
