package io.sentry.android.core.internal.util;

import android.os.SystemClock;
import io.sentry.transport.ICurrentDateProvider;
import org.jetbrains.annotations.ApiStatus;

/**
 * An uptime clock: {@link SystemClock#uptimeMillis()} excludes time the device spent in deep sleep,
 * which neither the name nor the {@link ICurrentDateProvider} type says. {@link
 * io.sentry.transport.CurrentDateProvider} implements that same type with epoch milliseconds, so a
 * call site declaring the interface accepts either, and the two disagree by however long the device
 * has been suspended.
 *
 * <p>Superseded by {@link io.sentry.time.MonotonicTicker}, which counts deep sleep and says in its
 * name that only differences between its own ticks are meaningful.
 */
@ApiStatus.Internal
public final class AndroidCurrentDateProvider implements ICurrentDateProvider {

  private static final ICurrentDateProvider instance = new AndroidCurrentDateProvider();

  /**
   * @deprecated use {@link io.sentry.time.MonotonicTicker} to measure an interval.
   */
  @Deprecated
  public static ICurrentDateProvider getInstance() {
    return instance;
  }

  private AndroidCurrentDateProvider() {}

  @Override
  public long getCurrentTimeMillis() {
    return SystemClock.uptimeMillis();
  }
}
