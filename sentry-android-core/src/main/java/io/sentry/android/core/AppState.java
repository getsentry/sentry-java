package io.sentry.android.core;

import io.sentry.ISentryLifecycleToken;
import io.sentry.util.AutoClosableReentrantLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** AppState holds the state of the App, e.g. whether the app is in background/foreground, etc. */
@ApiStatus.Internal
public final class AppState {
  private static @NotNull AppState instance = new AppState();
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

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

  void setInBackground(final boolean inBackground) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      this.inBackground = inBackground;
    }
  }
}
