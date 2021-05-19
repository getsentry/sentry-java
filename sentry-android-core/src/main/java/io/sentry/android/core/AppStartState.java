package io.sentry.android.core;

import android.os.SystemClock;
import io.sentry.DateUtils;
import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AppStartState {

  private static final @NotNull AppStartState instance = new AppStartState();

  private @Nullable Long appStart;
  private @Nullable Long appStartEnd;
  private @Nullable Boolean coldStart;
  private boolean sentStartMetric = false;
  private @Nullable Date appStartTime;
  private volatile boolean appStartSet = false;

  private AppStartState() {}

  static @NotNull AppStartState getInstance() {
    return instance;
  }

  void setAppStartEnd(final @Nullable Long appStartEnd) {
    this.appStartEnd = appStartEnd;
  }

  @Nullable
  Long getAppStartInterval() {
    if (appStart == null || appStartEnd == null || coldStart == null) {
      return null;
    }
    return appStartEnd - appStart;
  }

  boolean getColdStart() {
    return Boolean.TRUE.equals(coldStart);
  }

  void setColdStart(final boolean coldStart) {
    this.coldStart = coldStart;
  }

  boolean isSentStartMetric() {
    return sentStartMetric;
  }

  void setSentStartUp() {
    this.sentStartMetric = true;
  }

  @Nullable
  Date getAppStartTime() {
    return appStartTime;
  }

  synchronized void setAppStartTime() {
    if (appStartSet) {
      return;
    }
    final long millis = SystemClock.uptimeMillis();
    appStartTime = DateUtils.getCurrentDateTime();
    appStart = millis;
    appStartSet = true;
  }
}
