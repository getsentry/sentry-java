package io.sentry.android.core;

import static io.sentry.TypeCheckHint.ANDROID_ACTIVITY;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Automatically adds breadcrumbs for Activity Lifecycle Events. */
public final class ActivityBreadcrumbIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  private final @NotNull Application application;
  private @Nullable IHub hub;
  private boolean enabled;

  public ActivityBreadcrumbIntegration(final @NotNull Application application) {
    this.application = Objects.requireNonNull(application, "Application is required");
  }

  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    final SentryAndroidOptions androidOptions =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.hub = Objects.requireNonNull(hub, "Hub is required");
    this.enabled = androidOptions.isEnableActivityLifecycleBreadcrumbs();
    options
        .getLogger()
        .log(SentryLevel.DEBUG, "ActivityBreadcrumbIntegration enabled: %s", enabled);

    if (enabled) {
      application.registerActivityLifecycleCallbacks(this);
      options.getLogger().log(SentryLevel.DEBUG, "ActivityBreadcrumbIntegration installed.");
      addIntegrationToSdkVersion();
    }
  }

  @Override
  public void close() throws IOException {
    if (enabled) {
      application.unregisterActivityLifecycleCallbacks(this);
      if (hub != null) {
        hub.getOptions()
            .getLogger()
            .log(SentryLevel.DEBUG, "ActivityBreadcrumbIntegration removed.");
      }
    }
  }

  @Override
  public synchronized void onActivityCreated(
      final @NotNull Activity activity, final @Nullable Bundle savedInstanceState) {
    addBreadcrumb(activity, "created");
  }

  @Override
  public synchronized void onActivityStarted(final @NotNull Activity activity) {
    addBreadcrumb(activity, "started");
  }

  @SuppressLint("NewApi")
  @Override
  public synchronized void onActivityResumed(final @NotNull Activity activity) {
    addBreadcrumb(activity, "resumed");
  }

  @Override
  public synchronized void onActivityPaused(final @NotNull Activity activity) {
    addBreadcrumb(activity, "paused");
  }

  @Override
  public synchronized void onActivityStopped(final @NotNull Activity activity) {
    addBreadcrumb(activity, "stopped");
  }

  @Override
  public synchronized void onActivitySaveInstanceState(
      final @NotNull Activity activity, final @NotNull Bundle outState) {
    addBreadcrumb(activity, "saveInstanceState");
  }

  @Override
  public synchronized void onActivityDestroyed(final @NotNull Activity activity) {
    addBreadcrumb(activity, "destroyed");
  }

  private void addBreadcrumb(final @NotNull Activity activity, final @NotNull String state) {
    if (hub == null) {
      return;
    }

    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setType("navigation");
    breadcrumb.setData("state", state);
    breadcrumb.setData("screen", getActivityName(activity));
    breadcrumb.setCategory("ui.lifecycle");
    breadcrumb.setLevel(SentryLevel.INFO);

    final Hint hint = new Hint();
    hint.set(ANDROID_ACTIVITY, activity);

    hub.addBreadcrumb(breadcrumb, hint);
  }

  private @NotNull String getActivityName(final @NotNull Activity activity) {
    return activity.getClass().getSimpleName();
  }
}
