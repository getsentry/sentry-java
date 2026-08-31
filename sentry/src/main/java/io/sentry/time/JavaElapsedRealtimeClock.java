package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ElapsedRealtimeClock} backed by {@link System#nanoTime()}.
 *
 * <p>A JVM has no equivalent of Android's deep sleep that it can observe, so this is the same
 * source as {@link JavaUptimeClock}. The two are distinct types anyway, so that a call site
 * declaring which guarantee it needs keeps documenting that intent on every platform.
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
