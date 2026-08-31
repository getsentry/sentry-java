package io.sentry.android.core.internal.time;

import android.os.SystemClock;
import io.sentry.time.ElapsedRealtimeClock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ElapsedRealtimeClock} backed by {@link SystemClock#elapsedRealtimeNanos()}.
 *
 * <p>That is {@code CLOCK_BOOTTIME}, so it keeps counting while the device is suspended — unlike
 * {@link System#nanoTime()}, which the core module falls back to and which stops in deep sleep.
 */
@ApiStatus.Internal
public final class AndroidElapsedRealtimeClock implements ElapsedRealtimeClock {

  private static final AndroidElapsedRealtimeClock instance = new AndroidElapsedRealtimeClock();

  public static @NotNull ElapsedRealtimeClock getInstance() {
    return instance;
  }

  private AndroidElapsedRealtimeClock() {}

  @Override
  public long tickNanos() {
    return SystemClock.elapsedRealtimeNanos();
  }
}
