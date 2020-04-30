package io.sentry.android.core;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.sentry.core.Breadcrumb;
import io.sentry.core.IHub;
import io.sentry.core.Integration;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public final class ActivityBreadcrumbsIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  private final @NotNull Application application;
  private @Nullable IHub hub;
  private @Nullable SentryAndroidOptions options;

  public ActivityBreadcrumbsIntegration(final @NotNull Application application) {
    this.application = Objects.requireNonNull(application, "Application is required");
  }

  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.hub = Objects.requireNonNull(hub, "Hub is required");

    this.options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "ActivityBreadcrumbsIntegration enabled: %s",
            this.options.isEnableActivityLifecycleBreadcrumbs());

    if (this.options.isEnableActivityLifecycleBreadcrumbs()) {
      application.registerActivityLifecycleCallbacks(this);
      options.getLogger().log(SentryLevel.DEBUG, "ActivityBreadcrumbsIntegration installed.");
    }
  }

  @Override
  public void close() throws IOException {
    application.unregisterActivityLifecycleCallbacks(this);

    if (options != null) {
      options.getLogger().log(SentryLevel.DEBUG, "ActivityBreadcrumbsIntegration removed.");
    }
  }

  private void addBreadcrumb(final @NonNull Activity activity, final @NotNull String state) {
    if (hub != null) {
      final Breadcrumb breadcrumb = new Breadcrumb();
      breadcrumb.setType("navigation");
      breadcrumb.setData("state", state);
      breadcrumb.setData("screen", activity.getClass().getSimpleName());
      breadcrumb.setCategory("ui.lifecycle");
      breadcrumb.setLevel(SentryLevel.INFO);
      hub.addBreadcrumb(breadcrumb);
    }
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    addBreadcrumb(activity, "created");
  }

  @Override
  public void onActivityStarted(@NonNull Activity activity) {
    addBreadcrumb(activity, "started");
  }

  @Override
  public void onActivityResumed(@NonNull Activity activity) {
    addBreadcrumb(activity, "resumed");
  }

  @Override
  public void onActivityPaused(@NonNull Activity activity) {
    addBreadcrumb(activity, "paused");
  }

  @Override
  public void onActivityStopped(@NonNull Activity activity) {
    addBreadcrumb(activity, "stopped");
  }

  @Override
  public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    addBreadcrumb(activity, "saveInstanceState");
  }

  @Override
  public void onActivityDestroyed(@NonNull Activity activity) {
    addBreadcrumb(activity, "destroyed");
  }
}
