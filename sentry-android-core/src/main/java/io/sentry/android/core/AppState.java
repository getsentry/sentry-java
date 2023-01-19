package io.sentry.android.core;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** AppState holds the state of the App, e.g. whether the app is in background/foreground, etc. */
@ApiStatus.Internal
public final class AppState {
  private static @NotNull AppState instance = new AppState();

  private AppState() {}

  public static @NotNull AppState getInstance() {
    return instance;
  }

  private @Nullable Boolean inBackground = null;

  @TestOnly
  void resetInstance() {
    instance = new AppState();
  }

  public @Nullable Boolean isInBackground() {
    return inBackground;
  }

  synchronized void setInBackground(final boolean inBackground) {
    this.inBackground = inBackground;
  }
}
