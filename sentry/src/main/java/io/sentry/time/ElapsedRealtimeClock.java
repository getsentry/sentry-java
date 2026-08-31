package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;

/**
 * A {@link Ticker} that <strong>includes</strong> time the device spent suspended in deep sleep.
 *
 * <p>This is the clock for anything expressed in real elapsed time regardless of what the device
 * was doing — a rate-limit window the server asked us to wait out, or a cache entry that should go
 * stale on a wall-clock schedule.
 *
 * <p>On Android this is {@code CLOCK_BOOTTIME}, via {@code SystemClock.elapsedRealtimeNanos()}. On
 * the JVM there is no comparable suspend state, so uptime and elapsed real time coincide.
 */
@ApiStatus.Internal
public interface ElapsedRealtimeClock extends Ticker {}
