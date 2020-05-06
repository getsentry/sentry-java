package io.sentry.core.transport;

/** Date Provider to make the Transport unit testable */
interface ICurrentDateProvider {

  /**
   * Returns the current time in millis
   *
   * @return the time in millis
   */
  long getCurrentTimeMillis();
}
