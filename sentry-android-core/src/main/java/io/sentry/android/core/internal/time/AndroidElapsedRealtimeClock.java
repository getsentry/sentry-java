package io.sentry.android.core.internal.time;

import android.os.SystemClock;
import io.sentry.time.ElapsedRealtimeClock;
import org.jetbrains.annotations.ApiStatus;

/**
 * {@link ElapsedRealtimeClock} backed by {@link SystemClock#elapsedRealtimeNanos()}.
 *
 * <p>That is {@code CLOCK_BOOTTIME}, so it keeps counting while the device is suspended — unlike
 * {@link System#nanoTime()}, which the core module falls back to and which stops in deep sleep.
 */
@ApiStatus.Internal
public final class AndroidElapsedRealtimeClock implements ElapsedRealtimeClock {

  @Override
  public long tickNanos() {
    return SystemClock.elapsedRealtimeNanos();
  }
}
