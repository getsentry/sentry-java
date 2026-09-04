package io.sentry.transport;

import org.jetbrains.annotations.ApiStatus;

/**
 * The wall clock, which jumps when the device time changes.
 *
 * <p>Superseded by {@link io.sentry.SentryDateProvider} for timestamps and {@link
 * io.sentry.time.MonotonicClock} for intervals.
 */
@ApiStatus.Internal
public final class CurrentDateProvider implements ICurrentDateProvider {

  @SuppressWarnings("deprecation")
  private static final ICurrentDateProvider instance = new CurrentDateProvider();

  /**
   * @deprecated use {@link io.sentry.SentryDateProvider} for a timestamp, or {@link
   *     io.sentry.time.MonotonicClock} to measure an interval.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static ICurrentDateProvider getInstance() {
    return instance;
  }

  private CurrentDateProvider() {}

  @Override
  @SuppressWarnings("deprecation")
  public final long getCurrentTimeMillis() {
    return System.currentTimeMillis();
  }
}
