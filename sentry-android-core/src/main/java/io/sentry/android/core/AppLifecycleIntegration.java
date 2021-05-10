package io.sentry.android.core;

import androidx.lifecycle.ProcessLifecycleOwner;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.util.MainThreadChecker;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class AppLifecycleIntegration implements Integration, Closeable {

  @TestOnly @Nullable LifecycleWatcher watcher;

  private @Nullable SentryAndroidOptions options;

  private final @NotNull IHandler handler;

  public AppLifecycleIntegration() {
    this(new MainLooperHandler());
  }

  AppLifecycleIntegration(final @NotNull IHandler handler) {
    this.handler = handler;
  }

  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    Objects.requireNonNull(hub, "Hub is required");
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "enableSessionTracking enabled: %s",
            this.options.isEnableAutoSessionTracking());

    this.options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "enableAppLifecycleBreadcrumbs enabled: %s",
            this.options.isEnableAppLifecycleBreadcrumbs());

    if (this.options.isEnableAutoSessionTracking()
        || this.options.isEnableAppLifecycleBreadcrumbs()) {
      try {
        Class.forName("androidx.lifecycle.DefaultLifecycleObserver");
        Class.forName("androidx.lifecycle.ProcessLifecycleOwner");
        if (MainThreadChecker.isMainThread()) {
          addObserver(hub);
        } else {
          // some versions of the androidx lifecycle-process require this to be executed on the main
          // thread.
          handler.post(() -> addObserver(hub));
        }
      } catch (ClassNotFoundException e) {
        options
            .getLogger()
            .log(
                SentryLevel.INFO,
                "androidx.lifecycle is not available, AppLifecycleIntegration won't be installed",
                e);
      } catch (IllegalStateException e) {
        options
            .getLogger()
            .log(SentryLevel.ERROR, "AppLifecycleIntegration could not be installed", e);
      }
    }
  }

  private void addObserver(final @NotNull IHub hub) {
    watcher =
        new LifecycleWatcher(
            hub,
            this.options.getSessionTrackingIntervalMillis(),
            this.options.isEnableAutoSessionTracking(),
            this.options.isEnableAppLifecycleBreadcrumbs());
    ProcessLifecycleOwner.get().getLifecycle().addObserver(watcher);
    options.getLogger().log(SentryLevel.DEBUG, "AppLifecycleIntegration installed.");
  }

  private void removeObserver() {
    ProcessLifecycleOwner.get().getLifecycle().removeObserver(watcher);
  }

  @Override
  public void close() throws IOException {
    if (watcher != null) {
      if (MainThreadChecker.isMainThread()) {
        removeObserver();
      } else {
        // some versions of the androidx lifecycle-process require this to be executed on the main
        // thread.
        handler.post(() -> removeObserver());
      }
      watcher = null;
      if (options != null) {
        options.getLogger().log(SentryLevel.DEBUG, "AppLifecycleIntegration removed.");
      }
    }
  }
}
