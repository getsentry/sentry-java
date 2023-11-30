package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import androidx.lifecycle.ProcessLifecycleOwner;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.internal.util.AndroidMainThreadChecker;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class AppLifecycleIntegration implements Integration, Closeable {

  @TestOnly @Nullable volatile LifecycleWatcher watcher;

  private @Nullable SentryAndroidOptions options;

  private final @NotNull MainLooperHandler handler;

  public AppLifecycleIntegration() {
    this(new MainLooperHandler());
  }

  AppLifecycleIntegration(final @NotNull MainLooperHandler handler) {
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
        if (AndroidMainThreadChecker.getInstance().isMainThread()) {
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
    // this should never happen, check added to avoid warnings from NullAway
    if (this.options == null) {
      return;
    }

    watcher =
        new LifecycleWatcher(
            hub,
            this.options.getSessionTrackingIntervalMillis(),
            this.options.isEnableAutoSessionTracking(),
            this.options.isEnableAppLifecycleBreadcrumbs());

    try {
      ProcessLifecycleOwner.get().getLifecycle().addObserver(watcher);
      options.getLogger().log(SentryLevel.DEBUG, "AppLifecycleIntegration installed.");
      addIntegrationToSdkVersion(getClass());
    } catch (Throwable e) {
      // This is to handle a potential 'AbstractMethodError' gracefully. The error is triggered in
      // connection with conflicting dependencies of the androidx.lifecycle.
      // //See the issue here: https://github.com/getsentry/sentry-java/pull/2228
      watcher = null;
      options
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "AppLifecycleIntegration failed to get Lifecycle and could not be installed.",
              e);
    }
  }

  private void removeObserver() {
    final @Nullable LifecycleWatcher watcherRef = watcher;
    if (watcherRef != null) {
      ProcessLifecycleOwner.get().getLifecycle().removeObserver(watcherRef);
      if (options != null) {
        options.getLogger().log(SentryLevel.DEBUG, "AppLifecycleIntegration removed.");
      }
    }
    watcher = null;
  }

  @Override
  public void close() throws IOException {
    if (watcher == null) {
      return;
    }
    if (AndroidMainThreadChecker.getInstance().isMainThread()) {
      removeObserver();
    } else {
      // some versions of the androidx lifecycle-process require this to be executed on the main
      // thread.
      // avoid method refs on Android due to some issues with older AGP setups
      // noinspection Convert2MethodRef
      handler.post(() -> removeObserver());
    }
  }
}
