package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import androidx.lifecycle.ProcessLifecycleOwner;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.internal.util.AndroidThreadChecker;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class AppLifecycleIntegration implements Integration, Closeable {

  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();
  @TestOnly @Nullable volatile LifecycleWatcher watcher;

  private @Nullable SentryAndroidOptions options;

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    Objects.requireNonNull(scopes, "Scopes are required");
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
      try (final ISentryLifecycleToken ignored = lock.acquire()) {
        if (watcher != null) {
          return;
        }

        watcher =
            new LifecycleWatcher(
                scopes,
                this.options.getSessionTrackingIntervalMillis(),
                this.options.isEnableAutoSessionTracking(),
                this.options.isEnableAppLifecycleBreadcrumbs());

        AppState.getInstance().addAppStateListener(watcher);
      }

      options.getLogger().log(SentryLevel.DEBUG, "AppLifecycleIntegration installed.");
      addIntegrationToSdkVersion("AppLifecycle");
    }
  }

  private void removeObserver() {
    final @Nullable LifecycleWatcher watcherRef;
    try (final ISentryLifecycleToken ignored = lock.acquire()) {
      watcherRef = watcher;
      watcher = null;
    }

    if (watcherRef != null) {
      AppState.getInstance().removeAppStateListener(watcherRef);
      if (options != null) {
        options.getLogger().log(SentryLevel.DEBUG, "AppLifecycleIntegration removed.");
      }
    }
  }

  @Override
  public void close() throws IOException {
    removeObserver();
    // TODO: probably should move it to Scopes.close(), but that'd require a new interface and
    //  different implementations for Java and Android. This is probably fine like this too, because
    //  integrations are closed in the same place
    AppState.getInstance().removeLifecycleObserver();
  }
}
