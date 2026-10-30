package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** {@link MonotonicTicker} backed by {@link System#nanoTime()}. */
@ApiStatus.Internal
public final class JavaMonotonicTicker implements MonotonicTicker {

  private static final JavaMonotonicTicker instance = new JavaMonotonicTicker();

  /**
   * Prefer {@link io.sentry.SentryOptions#getMonotonicTicker()} over this: it resolves to the
   * ticker that is right for the platform the SDK is running on.
   *
   * <p>Android overrides that accessor with a {@code CLOCK_BOOTTIME}-backed ticker. This one stops
   * counting while the device is suspended, so measuring against it there silently under-reports
   * every interval that spans a deep sleep.
   *
   * <p>Call this directly only where there is no options object to ask — the default that {@code
   * SentryOptions} itself returns, or a test that means this implementation specifically.
   */
  public static @NotNull MonotonicTicker getInstance() {
    return instance;
  }

  private JavaMonotonicTicker() {}

  @Override
  public long tickNanos() {
    return System.nanoTime();
  }
}
