package io.sentry.android.core.internal.time;

import android.os.SystemClock;
import io.sentry.time.MonotonicTicker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * {@link MonotonicTicker} backed by {@link SystemClock#elapsedRealtimeNanos()}.
 *
 * <p>That is {@code CLOCK_BOOTTIME}, so it keeps counting while the device is suspended — unlike
 * {@link System#nanoTime()}, which the core module falls back to and which stops in deep sleep.
 */
@ApiStatus.Internal
public final class AndroidMonotonicTicker implements MonotonicTicker {

  private static final AndroidMonotonicTicker instance = new AndroidMonotonicTicker();

  public static @NotNull MonotonicTicker getInstance() {
    return instance;
  }

  private AndroidMonotonicTicker() {}

  @Override
  public long tickNanos() {
    return SystemClock.elapsedRealtimeNanos();
  }
}
