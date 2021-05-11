package io.sentry.android.core;

import java.util.Date;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
final class AppStartUpState {

  private static final @NotNull AppStartUpState instance = new AppStartUpState();

  private @Nullable Long appStartUp;
  private @Nullable Long appStartUpEnd;
  private @Nullable Boolean coldStartUp;
  private boolean sentStartUp = false;
  private @Nullable Date appStartTime;
//  private boolean readyToBeSent = false;

  private AppStartUpState() {}

  static @NotNull AppStartUpState getInstance() {
    return instance;
  }

  @Nullable
  Long getAppStartUp() {
    return appStartUp;
  }

  void setAppStartUp(final @Nullable Long appStartUp) {
    this.appStartUp = appStartUp;
  }

  @Nullable
  Long getAppStartUpEnd() {
    return appStartUpEnd;
  }

  void setAppStartUpEnd(final @Nullable Long appStartUpEnd) {
    // already set
    if (this.appStartUpEnd != null) {
      return;
    }
    this.appStartUpEnd = appStartUpEnd;
  }

  @Nullable
  Long getAppStartInterval() {
    if (appStartUp == null || appStartUpEnd == null || coldStartUp == null) {
      return null;
    }
    return appStartUpEnd - appStartUp;
  }

  boolean isColdStartUp() {
    return coldStartUp;
  }

  void setColdStartUp(boolean coldStartUp) {
    this.coldStartUp = coldStartUp;
  }

  boolean isSentStartUp() {
    return sentStartUp;
  }

  void setSentStartUp() {
    this.sentStartUp = true;
  }

  @Nullable
  Date getAppStartTime() {
    return appStartTime;
  }

  void setAppStartTime(@Nullable Date appStartTime) {
    this.appStartTime = appStartTime;
  }
}
