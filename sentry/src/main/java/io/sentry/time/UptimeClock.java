package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;

/**
 * A {@link Ticker} that <strong>excludes</strong> time the device spent suspended in deep sleep.
 *
 * <p>This is the clock for measuring how long the CPU was actually available — most importantly ANR
 * detection, where counting suspended time would report a responsive main thread as blocked.
 *
 * <p>On Android this is {@code CLOCK_MONOTONIC}, the same clock behind {@code
 * SystemClock.uptimeMillis()}. On the JVM there is no comparable suspend state, so uptime and
 * elapsed real time coincide.
 */
@ApiStatus.Internal
public interface UptimeClock extends Ticker {}
