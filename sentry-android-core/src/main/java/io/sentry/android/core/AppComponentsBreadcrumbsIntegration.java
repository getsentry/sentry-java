package io.sentry.android.core;

import static io.sentry.TypeCheckHint.ANDROID_CONFIGURATION;
import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
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

  private final @NotNull Context context;
  private @Nullable IHub hub;
  private @Nullable SentryAndroidOptions options;

  public AppComponentsBreadcrumbsIntegration(final @NotNull Context context) {
    this.context =
        Objects.requireNonNull(ContextUtils.getApplicationContext(context), "Context is required");
  }

  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    this.hub = Objects.requireNonNull(hub, "Hub is required");
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
        addIntegrationToSdkVersion(getClass());
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
    final long now = System.currentTimeMillis();
    executeInBackground(() -> captureLowMemoryBreadcrumb(now, null));
  }

  @Override
  public void onTrimMemory(final int level) {
    final long now = System.currentTimeMillis();
    executeInBackground(() -> captureLowMemoryBreadcrumb(now, level));
  }

  private void captureLowMemoryBreadcrumb(final long timeMs, final @Nullable Integer level) {
    if (hub != null) {
      final Breadcrumb breadcrumb = new Breadcrumb(timeMs);
      if (level != null) {
        // only add breadcrumb if TRIM_MEMORY_BACKGROUND, TRIM_MEMORY_MODERATE or
        // TRIM_MEMORY_COMPLETE.
        // Release as much memory as the process can.

        // TRIM_MEMORY_UI_HIDDEN, TRIM_MEMORY_RUNNING_MODERATE, TRIM_MEMORY_RUNNING_LOW and
        // TRIM_MEMORY_RUNNING_CRITICAL.
        // Release any memory that your app doesn't need to run.
        // So they are still not so critical at the point of killing the process.
        // https://developer.android.com/topic/performance/memory

        if (level < TRIM_MEMORY_BACKGROUND) {
          return;
        }
        breadcrumb.setData("level", level);
      }

      breadcrumb.setType("system");
      breadcrumb.setCategory("device.event");
      breadcrumb.setMessage("Low memory");
      breadcrumb.setData("action", "LOW_MEMORY");
      breadcrumb.setLevel(SentryLevel.WARNING);
      hub.addBreadcrumb(breadcrumb);
    }
  }

  private void captureConfigurationChangedBreadcrumb(
      final long timeMs, final @NotNull Configuration newConfig) {
    if (hub != null) {
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

      hub.addBreadcrumb(breadcrumb, hint);
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
