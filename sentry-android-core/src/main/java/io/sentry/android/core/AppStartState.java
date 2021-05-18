package io.sentry.android.core;

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

  private AppStartState() {}

  static @NotNull AppStartState getInstance() {
    return instance;
  }

  @Nullable
  Long getAppStart() {
    return appStart;
  }

  void setAppStart(final @Nullable Long appStart) {
    this.appStart = appStart;
  }

  @Nullable
  Long getAppStartEnd() {
    return appStartEnd;
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

  void setAppStartTime(final @Nullable Date appStartTime) {
    this.appStartTime = appStartTime;
  }
}
