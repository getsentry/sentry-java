package io.sentry.android.core.internal.time;

import android.os.SystemClock;
import io.sentry.time.MonotonicClock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * {@link MonotonicClock} backed by {@link SystemClock#elapsedRealtimeNanos()}.
 *
 * <p>That is {@code CLOCK_BOOTTIME}, so it keeps counting while the device is suspended — unlike
 * {@link System#nanoTime()}, which the core module falls back to and which stops in deep sleep.
 */
@ApiStatus.Internal
public final class AndroidMonotonicClock implements MonotonicClock {

  private static final AndroidMonotonicClock instance = new AndroidMonotonicClock();

  public static @NotNull MonotonicClock getInstance() {
    return instance;
  }

  private AndroidMonotonicClock() {}

  @Override
  public long tickNanos() {
    return SystemClock.elapsedRealtimeNanos();
  }
}
