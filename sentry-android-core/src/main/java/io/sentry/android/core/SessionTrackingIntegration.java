package io.sentry.android.core;

import androidx.lifecycle.ProcessLifecycleOwner;
import io.sentry.core.IHub;
import io.sentry.core.Integration;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class SessionTrackingIntegration implements Integration, Closeable {

  @TestOnly @Nullable LifecycleWatcher watcher;

  private @Nullable SentryOptions options;

  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "SentryOptions is required");
    Objects.requireNonNull(hub, "Hub is required");

    options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "SessionTrackingIntegration enabled: %s",
            options.isEnableSessionTracking());

    if (options.isEnableSessionTracking()) {
      try {
        Class.forName("androidx.lifecycle.DefaultLifecycleObserver");
        Class.forName("androidx.lifecycle.ProcessLifecycleOwner");
        watcher = new LifecycleWatcher(hub, options.getSessionTrackingIntervalMillis());
        ProcessLifecycleOwner.get().getLifecycle().addObserver(watcher);

        options.getLogger().log(SentryLevel.DEBUG, "SessionTrackingIntegration installed.");
      } catch (ClassNotFoundException e) {
        options
            .getLogger()
            .log(
                SentryLevel.INFO,
                "androidx.lifecycle is not available, SessionTrackingIntegration won't be installed",
                e);
      }
    }
  }

  @Override
  public void close() throws IOException {
    if (watcher != null) {
      ProcessLifecycleOwner.get().getLifecycle().removeObserver(watcher);
      watcher = null;
      if (options != null) {
        options.getLogger().log(SentryLevel.DEBUG, "SessionTrackingIntegration removed.");
      }
    }
  }
}
