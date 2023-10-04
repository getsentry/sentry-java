package io.sentry.android.core.performance;

import android.os.SystemClock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A measurement for time critical components on a macro (ms) level. Based on {@link
 * SystemClock#uptimeMillis()} to ensure linear time progression (as opposed to a syncable clock).
 * To provide real world unix time information, the start time is used and translated into unix
 * time. The stop unix time only gets projected based on the start time + duration of the time span.
 */
public class TimeSpan implements Comparable<TimeSpan> {

  private @Nullable String description;

  private long startUnixTimeMs;
  private long startUptimeMs;
  private long stopUptimeMs;

  /** Start the time span */
  public void start() {
    startUptimeMs = SystemClock.uptimeMillis();
    startUnixTimeMs = System.currentTimeMillis();
  }

  /**
   * @param uptimeMs the uptime in ms, provided by {@link SystemClock#uptimeMillis()}
   */
  public void setStartedAt(final long uptimeMs) {
    // TODO maybe sanity check?
    this.startUptimeMs = uptimeMs;

    final long shiftMs = SystemClock.uptimeMillis() - startUptimeMs;
    startUnixTimeMs = System.currentTimeMillis() - shiftMs;
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
   * @return the start timestamp of this measurement, unix time, in ms
   */
  public long getStartTimestampMs() {
    return startUnixTimeMs;
  }

  /**
   * @return the start timestamp of this measurement, unix time, in ms
   */
  public double getStartTimestampS() {
    return (double) startUnixTimeMs / 1000.0d;
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

  public double getProjectedStopTimestampS() {
    return (double) getProjectedStopTimestampMs() / 1000.0d;
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

  public @Nullable String getDescription() {
    return description;
  }

  public void setDescription(@Nullable final String description) {
    this.description = description;
  }

  public void reset() {
    startUptimeMs = 0;
    stopUptimeMs = 0;
    startUnixTimeMs = 0;
  }

  @Override
  public int compareTo(@NotNull final TimeSpan o) {
    return Long.compare(startUnixTimeMs, o.startUnixTimeMs);
  }
}
