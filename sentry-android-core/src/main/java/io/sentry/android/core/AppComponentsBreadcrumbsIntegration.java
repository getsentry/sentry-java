package io.sentry.android.core;

import static io.sentry.TypeCheckHint.ANDROID_CONFIGURATION;
import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.internal.util.AndroidCurrentDateProvider;
import io.sentry.android.core.internal.util.Debouncer;
import io.sentry.android.core.internal.util.DeviceOrientations;
import io.sentry.protocol.Device;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AppComponentsBreadcrumbsIntegration
    implements Integration, Closeable, ComponentCallbacks2 {

  private static final long DEBOUNCE_WAIT_TIME_MS = 60 * 1000;
  // pre-allocate hint to avoid creating it every time for the low memory case
  private static final @NotNull Hint EMPTY_HINT = new Hint();

  private final @NotNull Context context;
  private @Nullable IScopes scopes;
  private @Nullable SentryAndroidOptions options;

  private final @NotNull Debouncer trimMemoryDebouncer =
      new Debouncer(AndroidCurrentDateProvider.getInstance(), DEBOUNCE_WAIT_TIME_MS, 0);

  public AppComponentsBreadcrumbsIntegration(final @NotNull Context context) {
    this.context =
        Objects.requireNonNull(ContextUtils.getApplicationContext(context), "Context is required");
  }

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "AppComponentsBreadcrumbsIntegration enabled: %s",
            this.options.isEnableAppComponentBreadcrumbs());

    if (this.options.isEnableAppComponentBreadcrumbs()) {
      try {
        // if its a ContextImpl, registerComponentCallbacks can't be used
        context.registerComponentCallbacks(this);
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "AppComponentsBreadcrumbsIntegration installed.");
        addIntegrationToSdkVersion("AppComponentsBreadcrumbs");
      } catch (Throwable e) {
        this.options.setEnableAppComponentBreadcrumbs(false);
        options.getLogger().log(SentryLevel.INFO, e, "ComponentCallbacks2 is not available.");
      }
    }
  }

  @Override
  public void close() throws IOException {
    try {
      // if its a ContextImpl, unregisterComponentCallbacks can't be used
      context.unregisterComponentCallbacks(this);
    } catch (Throwable ignored) {
      // fine, might throw on older versions
      if (options != null) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, ignored, "It was not possible to unregisterComponentCallbacks");
      }
    }

    if (options != null) {
      options.getLogger().log(SentryLevel.DEBUG, "AppComponentsBreadcrumbsIntegration removed.");
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void onConfigurationChanged(@NotNull Configuration newConfig) {
    final long now = System.currentTimeMillis();
    executeInBackground(() -> captureConfigurationChangedBreadcrumb(now, newConfig));
  }

  @Override
  public void onLowMemory() {
    // we do this in onTrimMemory below already, this is legacy API (14 or below)
  }

  @Override
  public void onTrimMemory(final int level) {
    if (level < TRIM_MEMORY_BACKGROUND) {
      // only add breadcrumb if TRIM_MEMORY_BACKGROUND, TRIM_MEMORY_MODERATE or
      // TRIM_MEMORY_COMPLETE.
      // Release as much memory as the process can.

      // TRIM_MEMORY_UI_HIDDEN, TRIM_MEMORY_RUNNING_MODERATE, TRIM_MEMORY_RUNNING_LOW and
      // TRIM_MEMORY_RUNNING_CRITICAL.
      // Release any memory that your app doesn't need to run.
      // So they are still not so critical at the point of killing the process.
      // https://developer.android.com/topic/performance/memory
      return;
    }

    if (trimMemoryDebouncer.checkForDebounce()) {
      // if we received trim_memory within 1 minute time, ignore this call
      return;
    }

    final long now = System.currentTimeMillis();
    executeInBackground(() -> captureLowMemoryBreadcrumb(now, level));
  }

  private void captureLowMemoryBreadcrumb(final long timeMs, final int level) {
    if (scopes != null) {
      final Breadcrumb breadcrumb = new Breadcrumb(timeMs);
      breadcrumb.setType("system");
      breadcrumb.setCategory("device.event");
      breadcrumb.setMessage("Low memory");
      breadcrumb.setData("action", "LOW_MEMORY");
      breadcrumb.setData("level", level);
      breadcrumb.setLevel(SentryLevel.WARNING);
      scopes.addBreadcrumb(breadcrumb, EMPTY_HINT);
    }
  }

  private void captureConfigurationChangedBreadcrumb(
      final long timeMs, final @NotNull Configuration newConfig) {
    if (scopes != null) {
      final Device.DeviceOrientation deviceOrientation =
          DeviceOrientations.getOrientation(context.getResources().getConfiguration().orientation);

      String orientation;
      if (deviceOrientation != null) {
        orientation = deviceOrientation.name().toLowerCase(Locale.ROOT);
      } else {
        orientation = "undefined";
      }

      final Breadcrumb breadcrumb = new Breadcrumb(timeMs);
      breadcrumb.setType("navigation");
      breadcrumb.setCategory("device.orientation");
      breadcrumb.setData("position", orientation);
      breadcrumb.setLevel(SentryLevel.INFO);

      final Hint hint = new Hint();
      hint.set(ANDROID_CONFIGURATION, newConfig);

      scopes.addBreadcrumb(breadcrumb, hint);
    }
  }

  private void executeInBackground(final @NotNull Runnable runnable) {
    if (options != null) {
      try {
        options.getExecutorService().submit(runnable);
      } catch (Throwable t) {
        options
            .getLogger()
            .log(SentryLevel.ERROR, t, "Failed to submit app components breadcrumb task");
      }
    }
  }
}
