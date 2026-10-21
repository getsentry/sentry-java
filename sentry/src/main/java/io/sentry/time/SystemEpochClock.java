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
