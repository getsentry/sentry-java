package io.sentry.android.core;

import static io.sentry.TypeCheckHint.ANDROID_ACTIVITY;
import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Automatically adds breadcrumbs for Activity Lifecycle Events. */
public final class ActivityBreadcrumbsIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  private final @NotNull Application application;
  private @Nullable IScopes scopes;
  private boolean enabled;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  // TODO check if locking is even required at all for lifecycle methods
  public ActivityBreadcrumbsIntegration(final @NotNull Application application) {
    this.application = Objects.requireNonNull(application, "Application is required");
  }

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    final SentryAndroidOptions androidOptions =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");
    this.enabled = androidOptions.isEnableActivityLifecycleBreadcrumbs();
    options
        .getLogger()
        .log(SentryLevel.DEBUG, "ActivityBreadcrumbsIntegration enabled: %s", enabled);

    if (enabled) {
      application.registerActivityLifecycleCallbacks(this);
      options.getLogger().log(SentryLevel.DEBUG, "ActivityBreadcrumbIntegration installed.");
      addIntegrationToSdkVersion("ActivityBreadcrumbs");
    }
  }

  @Override
  public void close() throws IOException {
    if (enabled) {
      application.unregisterActivityLifecycleCallbacks(this);
      if (scopes != null) {
        scopes
            .getOptions()
            .getLogger()
            .log(SentryLevel.DEBUG, "ActivityBreadcrumbsIntegration removed.");
      }
    }
  }

  @Override
  public void onActivityCreated(
      final @NotNull Activity activity, final @Nullable Bundle savedInstanceState) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      addBreadcrumb(activity, "created");
    }
  }

  @Override
  public void onActivityStarted(final @NotNull Activity activity) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      addBreadcrumb(activity, "started");
    }
  }

  @Override
  public void onActivityResumed(final @NotNull Activity activity) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      addBreadcrumb(activity, "resumed");
    }
  }

  @Override
  public void onActivityPaused(final @NotNull Activity activity) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      addBreadcrumb(activity, "paused");
    }
  }

  @Override
  public void onActivityStopped(final @NotNull Activity activity) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      addBreadcrumb(activity, "stopped");
    }
  }

  @Override
  public void onActivitySaveInstanceState(
      final @NotNull Activity activity, final @NotNull Bundle outState) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      addBreadcrumb(activity, "saveInstanceState");
    }
  }

  @Override
  public void onActivityDestroyed(final @NotNull Activity activity) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      addBreadcrumb(activity, "destroyed");
    }
  }

  private void addBreadcrumb(final @NotNull Activity activity, final @NotNull String state) {
    if (scopes == null) {
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

    scopes.addBreadcrumb(breadcrumb, hint);
  }

  private @NotNull String getActivityName(final @NotNull Activity activity) {
    return activity.getClass().getSimpleName();
  }
}
