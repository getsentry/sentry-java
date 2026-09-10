package io.sentry.time;

import io.sentry.DateUtils;
import io.sentry.util.Platform;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The {@link EpochClock} backed by the system wall clock.
 *
 * <p>Reads the epoch at the best precision the platform offers: {@link java.time.Instant} where it
 * is sub-millisecond, {@link System#currentTimeMillis()} everywhere else. Android is always the
 * latter — {@code Instant} is millisecond-granular there whether or not the build desugars it, see
 * https://github.com/getsentry/sentry-java/pull/2451.
 *
 * <p>A millisecond anchor loses less than it looks: an {@link AnchoredClock} adds nanosecond ticks
 * to one anchor, so only the anchor is coarse.
 */
@ApiStatus.Internal
public final class SystemEpochClock implements EpochClock {

  private static final boolean INSTANT_IS_SUB_MILLISECOND =
      Platform.isJvm() && Platform.isJavaNinePlus();

  private static final SystemEpochClock instance = new SystemEpochClock();

  /**
   * Prefer {@link io.sentry.SentryOptions#getEpochClock()} over this: that accessor is where a
   * platform-specific wall clock would be substituted, the way {@code SentryAndroidOptions} already
   * substitutes the ticker. Reading it keeps a call site from being pinned to this implementation.
   *
   * <p>Call this directly only where there is no options object to ask — the default that {@code
   * SentryOptions} itself returns, or a test that means this implementation specifically.
   */
  public static @NotNull EpochClock getInstance() {
    return instance;
  }

  private SystemEpochClock() {}

  @Override
  public @NotNull Timestamp now() {
    return Timestamp.ofEpochNanos(
        INSTANT_IS_SUB_MILLISECOND
            ? InstantEpochNanos.read()
            : DateUtils.millisToNanos(System.currentTimeMillis()));
  }
}
