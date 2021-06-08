package io.sentry.android.core;

import android.os.SystemClock;
import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** AppStartState holds the state of the App Start metric and appStartTime */
final class AppStartState {

  private static @NotNull AppStartState instance = new AppStartState();

  private @Nullable Long appStartMillis;

  private @Nullable Long appStartEndMillis;

  /** The type of App start coldStart=true -> Cold start, coldStart=false -> Warm start */
  private boolean coldStart;

  /** appStart as a Date used in the App's Context */
  private @Nullable Date appStartTime;

  private AppStartState() {}

  static @NotNull AppStartState getInstance() {
    return instance;
  }

  @TestOnly
  void resetInstance() {
    instance = new AppStartState();
  }

  void setAppStartEnd() {
    setAppStartEnd(SystemClock.uptimeMillis());
  }

  @TestOnly
  void setAppStartEnd(final long appStartEndMillis) {
    this.appStartEndMillis = appStartEndMillis;
  }

  @Nullable
  Long getAppStartInterval() {
    if (appStartMillis == null || appStartEndMillis == null) {
      return null;
    }
    return appStartEndMillis - appStartMillis;
  }

  boolean isColdStart() {
    return coldStart;
  }

  void setColdStart(final boolean coldStart) {
    this.coldStart = coldStart;
  }

  @Nullable
  Date getAppStartTime() {
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
