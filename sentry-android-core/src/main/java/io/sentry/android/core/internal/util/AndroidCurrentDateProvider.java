package io.sentry.android.core.internal.util;

import android.os.SystemClock;
import io.sentry.transport.ICurrentDateProvider;
import org.jetbrains.annotations.ApiStatus;

/**
 * An uptime clock: {@link SystemClock#uptimeMillis()} excludes time the device spent in deep sleep,
 * which the name does not say.
 *
 * <p>Superseded by {@link io.sentry.time.MonotonicClock}, which counts deep sleep and says so in
 * its name.
 */
@ApiStatus.Internal
public final class AndroidCurrentDateProvider implements ICurrentDateProvider {

  @SuppressWarnings("deprecation")
  private static final ICurrentDateProvider instance = new AndroidCurrentDateProvider();

  /**
   * @deprecated use {@link io.sentry.time.MonotonicClock} to measure an interval.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static ICurrentDateProvider getInstance() {
    return instance;
  }

  private AndroidCurrentDateProvider() {}

  @Override
  @SuppressWarnings("deprecation")
  public long getCurrentTimeMillis() {
    return SystemClock.uptimeMillis();
  }
}
