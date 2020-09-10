package io.sentry.transport;

import org.jetbrains.annotations.ApiStatus;

/** Date Provider to make the Transport unit testable */
@ApiStatus.Internal
public interface ICurrentDateProvider {

  /**
   * Returns the current time in millis
   *
   * @return the time in millis
   */
  long getCurrentTimeMillis();
}
