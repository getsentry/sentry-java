package io.sentry.util;

import io.sentry.CheckIn;
import io.sentry.CheckInStatus;
import io.sentry.DateUtils;
import io.sentry.FilterString;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.MonitorConfig;
import io.sentry.Sentry;
import io.sentry.protocol.SentryId;
import java.util.List;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class CheckInUtils {

  /**
   * Helper method to send monitor check-ins for a {@link Callable}
   *
   * @param monitorSlug - the slug of the monitor
   * @param monitorConfig - configuration of the monitor, can be used for upserting schedule
   * @param environment - the name of the environment
   * @param callable - the {@link Callable} to be called
   * @return the return value of the {@link Callable}
   * @param <U> - the result type of the {@link Callable}
   */
  public static <U> U withCheckIn(
      final @NotNull String monitorSlug,
      final @Nullable String environment,
      final @Nullable MonitorConfig monitorConfig,
      final @NotNull Callable<U> callable)
      throws Exception {
    try (final @NotNull ISentryLifecycleToken ignored =
            Sentry.forkedScopes("CheckInUtils").makeCurrent()) {
      final @NotNull IScopes scopes = Sentry.getCurrentScopes();
      final long startTime = System.currentTimeMillis();
      boolean didError = false;

      TracingUtils.startNewTrace(scopes);

      CheckIn inProgressCheckIn = new CheckIn(monitorSlug, CheckInStatus.IN_PROGRESS);
      if (monitorConfig != null) {
        inProgressCheckIn.setMonitorConfig(monitorConfig);
      }
      if (environment != null) {
        inProgressCheckIn.setEnvironment(environment);
      }
      @Nullable SentryId checkInId = scopes.captureCheckIn(inProgressCheckIn);
      try {
        return callable.call();
      } catch (Throwable t) {
        didError = true;
        throw t;
      } finally {
        final @NotNull CheckInStatus status = didError ? CheckInStatus.ERROR : CheckInStatus.OK;
        CheckIn checkIn = new CheckIn(checkInId, monitorSlug, status);
        if (environment != null) {
          checkIn.setEnvironment(environment);
        }
        checkIn.setDuration(DateUtils.millisToSeconds(System.currentTimeMillis() - startTime));
        scopes.captureCheckIn(checkIn);
      }
    }
  }

  /**
   * Helper method to send monitor check-ins for a {@link Callable}
   *
   * @param monitorSlug - the slug of the monitor
   * @param monitorConfig - configuration of the monitor, can be used for upserting schedule
   * @param callable - the {@link Callable} to be called
   * @return the return value of the {@link Callable}
   * @param <U> - the result type of the {@link Callable}
   */
  public static <U> U withCheckIn(
      final @NotNull String monitorSlug,
      final @Nullable MonitorConfig monitorConfig,
      final @NotNull Callable<U> callable)
      throws Exception {
    return withCheckIn(monitorSlug, null, monitorConfig, callable);
  }

  /**
   * Helper method to send monitor check-ins for a {@link Callable}
   *
   * @param monitorSlug - the slug of the monitor
   * @param environment - the name of the environment
   * @param callable - the {@link Callable} to be called
   * @return the return value of the {@link Callable}
   * @param <U> - the result type of the {@link Callable}
   */
  public static <U> U withCheckIn(
      final @NotNull String monitorSlug,
      final @Nullable String environment,
      final @NotNull Callable<U> callable)
      throws Exception {
    return withCheckIn(monitorSlug, environment, null, callable);
  }

  /**
   * Helper method to send monitor check-ins for a {@link Callable}
   *
   * @param monitorSlug - the slug of the monitor
   * @param callable - the {@link Callable} to be called
   * @return the return value of the {@link Callable}
   * @param <U> - the result type of the {@link Callable}
   */
  public static <U> U withCheckIn(
      final @NotNull String monitorSlug, final @NotNull Callable<U> callable) throws Exception {
    return withCheckIn(monitorSlug, null, null, callable);
  }

  /** Checks if a check-in for a monitor (CRON) has been ignored. */
  @ApiStatus.Internal
  public static boolean isIgnored(
      final @Nullable List<FilterString> ignoredSlugs, final @NotNull String slug) {
    if (ignoredSlugs == null || ignoredSlugs.isEmpty()) {
      return false;
    }

    for (final FilterString ignoredSlug : ignoredSlugs) {
      if (ignoredSlug.getFilterString().equalsIgnoreCase(slug)) {
        return true;
      }
    }

    for (final FilterString ignoredSlug : ignoredSlugs) {
      try {
        if (ignoredSlug.matches(slug)) {
          return true;
        }
      } catch (Throwable t) {
        // ignore invalid regex
      }
    }

    return false;
  }
}
