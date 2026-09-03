package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;

/**
 * A monotonically increasing nanosecond counter, including time the device spent suspended in deep
 * sleep.
 *
 * <p>This type deliberately promises very little: a tick is a number that does not go backwards,
 * measured from an origin that is arbitrary and may be negative. Only <em>differences</em> between
 * two ticks from the same instance are meaningful, and a tick must never be persisted, serialized,
 * or compared against a value from another clock.
 *
 * <p>On Android this is {@code CLOCK_BOOTTIME}, via {@code SystemClock.elapsedRealtimeNanos()}, so
 * an interval measured across a suspend reports the real time that passed rather than only the time
 * the CPU was awake. On the JVM there is no comparable suspend state, so {@link System#nanoTime()}
 * is equivalent.
 */
@ApiStatus.Internal
public interface MonotonicClock {
  long tickNanos();
}
