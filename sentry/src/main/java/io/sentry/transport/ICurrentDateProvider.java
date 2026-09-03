package io.sentry.transport;

import org.jetbrains.annotations.ApiStatus;

/**
 * Date Provider to make the Transport unit testable
 *
 * <p>Superseded by {@link io.sentry.time.MonotonicClock}. The name here does not say which clock
 * the value comes from, and implementations disagreed: {@link CurrentDateProvider} returns wall
 * time while {@code AndroidCurrentDateProvider} returns uptime, through this one type.
 */
@ApiStatus.Internal
public interface ICurrentDateProvider {

  /**
   * Returns the current time in millis
   *
   * @return the time in millis
   * @deprecated use {@link io.sentry.time.MonotonicClock} to measure an interval, or {@link
   *     io.sentry.SentryDateProvider} for a wall-clock timestamp.
   */
  @Deprecated
  long getCurrentTimeMillis();
}
