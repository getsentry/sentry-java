package io.sentry.android.core;

import android.os.SystemClock;
import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** AppStartState holds the state of the App Start metric and appStartTime */
final class AppStartState {

  private static @NotNull AppStartState instance = new AppStartState();

  /** appStart in milliseconds */
  private @Nullable Long appStart;

  /** appStartEnd in milliseconds */
  private @Nullable Long appStartEnd;

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
    appStartEnd = SystemClock.uptimeMillis();
  }

  @Nullable
  Long getAppStartInterval() {
    if (appStart == null || appStartEnd == null) {
      return null;
    }
    return appStartEnd - appStart;
  }

  boolean getColdStart() {
    return coldStart;
  }

  void setColdStart(final boolean coldStart) {
    this.coldStart = coldStart;
  }

  @Nullable
  Date getAppStartTime() {
    return appStartTime;
  }

  synchronized void setAppStartTime(final long appStart, final @NotNull Date appStartTime) {
    // method is synchronized because the SDK may by init. on a background thread.
    if (this.appStartTime != null && this.appStart != null) {
      return;
    }
    this.appStartTime = appStartTime;
    this.appStart = appStart;
  }
}
