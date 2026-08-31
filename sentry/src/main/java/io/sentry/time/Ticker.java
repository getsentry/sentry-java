package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;

/**
 * A monotonically increasing nanosecond counter.
 *
 * <p>This type deliberately promises very little: a tick is a number that does not go backwards,
 * measured from an origin that is arbitrary and may be negative. Only <em>differences</em> between
 * two ticks from the same instance are meaningful, and a tick must never be persisted, serialized,
 * or compared against a value from another clock.
 *
 * <p>Do not implement or depend on {@code Ticker} directly. It exists so that {@link Deadline} and
 * {@link Stopwatch} can be written once; callers declare {@link UptimeClock} or {@link
 * ElapsedRealtimeClock}, whose names state which guarantee they provide.
 */
@ApiStatus.Internal
public interface Ticker {
  long tickNanos();
}
