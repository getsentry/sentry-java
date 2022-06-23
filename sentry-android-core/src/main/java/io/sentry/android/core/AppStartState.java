package io.sentry.android.core;

import android.os.SystemClock;
import java.util.Date;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** AppStartState holds the state of the App Start metric and appStartTime */
@ApiStatus.Internal
public final class AppStartState {

  private static @NotNull AppStartState instance = new AppStartState();

  /** // We filter out App starts more than 60s */
  private static final int MAX_APP_START_MILLIS = 60000;

  private @Nullable Long appStartMillis;

  private @Nullable Long appStartEndMillis;

  /** The type of App start coldStart=true -> Cold start, coldStart=false -> Warm start */
  private @Nullable Boolean coldStart = null;

  /** appStart as a Date used in the App's Context */
  private @Nullable Date appStartTime;

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

    // we filter out app start more than 60s.
    // this could be due to many different reasons.
    // we've seen app starts with hours, days and even months.
    if (appStart >= MAX_APP_START) {
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
  public Date getAppStartTime() {
    return appStartTime;
  }

  synchronized void setAppStartTime(final long appStartMillis, final @NotNull Date appStartTime) {
    // method is synchronized because the SDK may by init. on a background thread.
    if (this.appStartTime != null && this.appStartMillis != null) {
      return;
    }
    this.appStartTime = appStartTime;
    this.appStartMillis = appStartMillis;
  }
}
