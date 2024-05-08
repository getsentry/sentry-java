package io.sentry.android.core.performance;

import android.os.SystemClock;
import io.sentry.DateUtils;
import io.sentry.SentryDate;
import io.sentry.SentryLongDate;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * A measurement for time critical components on a macro (ms) level. Based on {@link
 * SystemClock#uptimeMillis()} to ensure linear time progression (as opposed to a syncable clock).
 * To provide real world unix time information, the start uptime time is stored alongside the unix
 * time. The stop unix time is artificial, it gets projected based on the start time + duration of
 * the time span.
 */
@ApiStatus.Internal
public class TimeSpan implements Comparable<TimeSpan> {

  private @Nullable String description;

  private long startSystemNanos;
  private long startUnixTimeMs;
  private long startUptimeMs;
  private long stopUptimeMs;

  /** Start the time span */
  public void start() {
    startUptimeMs = SystemClock.uptimeMillis();
    startUnixTimeMs = System.currentTimeMillis();
    startSystemNanos = System.nanoTime();
  }

  /**
   * @param uptimeMs the uptime in ms, provided by {@link SystemClock#uptimeMillis()}
   */
  public void setStartedAt(final long uptimeMs) {
    // TODO maybe sanity check?
    this.startUptimeMs = uptimeMs;

    final long shiftMs = SystemClock.uptimeMillis() - startUptimeMs;
    startUnixTimeMs = System.currentTimeMillis() - shiftMs;
    startSystemNanos = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(shiftMs);
  }

  /** Stops the time span */
  public void stop() {
    stopUptimeMs = SystemClock.uptimeMillis();
  }

  /**
   * @param uptimeMs the uptime in ms, provided by {@link SystemClock#uptimeMillis()}
   */
  public void setStoppedAt(final long uptimeMs) {
    // TODO maybe sanity check?
    stopUptimeMs = uptimeMs;
  }

  public boolean hasStarted() {
    return startUptimeMs != 0;
  }

  public boolean hasNotStarted() {
    return startUptimeMs == 0;
  }

  public boolean hasStopped() {
    return stopUptimeMs != 0;
  }

  public boolean hasNotStopped() {
    return stopUptimeMs == 0;
  }

  /**
   * @return the start timestamp of this measurement, as uptime, in ms
   */
  public long getStartUptimeMs() {
    return startUptimeMs;
  }

  /**
   * @return the start timestamp of this measurement, unix time, in ms
   */
  public long getStartTimestampMs() {
    return startUnixTimeMs;
  }

  /**
   * @return the start timestamp of this measurement, unix time
   */
  public @Nullable SentryDate getStartTimestamp() {
    if (hasStarted()) {
      return new SentryLongDate(DateUtils.millisToNanos(getStartTimestampMs()));
    }
    return null;
  }

  /**
   * @return the start timestamp of this measurement, unix time, in seconds
   */
  public double getStartTimestampSecs() {
    return DateUtils.millisToSeconds(startUnixTimeMs);
  }

  /**
   * @return the projected stop timestamp of this measurement, based on the start timestamp and the
   *     duration. If the time span was not started 0 is returned, if the time span was not stopped
   *     the start timestamp is returned.
   */
  public long getProjectedStopTimestampMs() {
    if (hasStarted()) {
      return startUnixTimeMs + getDurationMs();
    }
    return 0;
  }

  /**
   * @return the projected stop timestamp
   * @see #getProjectedStopTimestampMs()
   */
  public double getProjectedStopTimestampSecs() {
    return DateUtils.millisToSeconds(getProjectedStopTimestampMs());
  }

  /**
   * @return the projected stop timestamp
   * @see #getProjectedStopTimestampMs()
   */
  public @Nullable SentryDate getProjectedStopTimestamp() {
    if (hasStopped()) {
      return new SentryLongDate(DateUtils.millisToNanos(getProjectedStopTimestampMs()));
    }
    return null;
  }

  /**
   * @return the duration of this measurement, in ms, or 0 if no end time is set
   */
  public long getDurationMs() {
    if (hasStopped()) {
      return stopUptimeMs - startUptimeMs;
    } else {
      return 0;
    }
  }

  @TestOnly
  public void setStartUnixTimeMs(long startUnixTimeMs) {
    this.startUnixTimeMs = startUnixTimeMs;
  }

  public @Nullable String getDescription() {
    return description;
  }

  public void setDescription(@Nullable final String description) {
    this.description = description;
  }

  public void reset() {
    description = null;
    startUptimeMs = 0;
    stopUptimeMs = 0;
    startUnixTimeMs = 0;
    startSystemNanos = 0;
  }

  @Override
  public int compareTo(@NotNull final TimeSpan o) {
    return Long.compare(startUnixTimeMs, o.startUnixTimeMs);
  }
}
