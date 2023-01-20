package io.sentry.android.core;

import android.os.SystemClock;
import io.sentry.SentryDate;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** AppStartState holds the state of the App Start metric and appStartTime */
@ApiStatus.Internal
public final class AppStartState {

  private static @NotNull AppStartState instance = new AppStartState();

  /** We filter out App starts more than 60s */
  private static final int MAX_APP_START_MILLIS = 60000;

  private @Nullable Long appStartMillis;

  private @Nullable Long appStartEndMillis;

  /** The type of App start coldStart=true -> Cold start, coldStart=false -> Warm start */
  private @Nullable Boolean coldStart = null;

  /** appStart as a Date used in the App's Context */
  private @Nullable SentryDate appStartTime;

  private AppStartState() {}

  public static @NotNull AppStartState getInstance() {
    return instance;
  }

  @TestOnly
  void resetInstance() {
    instance = new AppStartState();
  }

  synchronized void setAppStartEnd() {
    setAppStartEnd(SystemClock.uptimeMillis());
  }

  @TestOnly
  void setAppStartEnd(final long appStartEndMillis) {
    this.appStartEndMillis = appStartEndMillis;
  }

  @Nullable
  public synchronized Long getAppStartInterval() {
    if (appStartMillis == null || appStartEndMillis == null || coldStart == null) {
      return null;
    }
    final long appStart = appStartEndMillis - appStartMillis;

    // We filter out app start more than 60s.
    // This could be due to many different reasons.
    // If you do the manual init and init the SDK too late and it does not compute the app start end
    // in the very first Activity.
    // If the process starts but the App isn't in the foreground.
    // If the system forked the zygote earlier to accelerate the app start.
    // And some unknown reasons that could not be reproduced.
    // We've seen app starts with hours, days and even months.
    if (appStart >= MAX_APP_START_MILLIS) {
      return null;
    }

    return appStart;
  }

  public @Nullable Boolean isColdStart() {
    return coldStart;
  }

  synchronized void setColdStart(final boolean coldStart) {
    if (this.coldStart != null) {
      return;
    }
    this.coldStart = coldStart;
  }

  @Nullable
  public SentryDate getAppStartTime() {
    return appStartTime;
  }

  @Nullable
  public Long getAppStartMillis() {
    return appStartMillis;
  }

  synchronized void setAppStartTime(
      final long appStartMillis, final @NotNull SentryDate appStartTime) {
    // method is synchronized because the SDK may by init. on a background thread.
    if (this.appStartTime != null && this.appStartMillis != null) {
      return;
    }
    this.appStartTime = appStartTime;
    this.appStartMillis = appStartMillis;
  }

  @TestOnly
  public synchronized void setAppStartMillis(final long appStartMillis) {
    this.appStartMillis = appStartMillis;
  }

  @TestOnly
  public synchronized void reset() {
    appStartTime = null;
    appStartMillis = null;
    appStartEndMillis = null;
  }
}
