package io.sentry.android.core;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.WeakHashMap;
import org.jetbrains.annotations.NotNull;

public final class ActivityLifecycleIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  private final @NotNull Application application;
  private @NotNull IHub hub;
  private @NotNull SentryAndroidOptions options;

  private boolean performanceEnabled = false;

  // WeakHashMap isn't thread safe but ActivityLifecycleCallbacks is only called from the
  // main-thread
  private final @NotNull WeakHashMap<Activity, ITransaction> activities = new WeakHashMap<>();

  public ActivityLifecycleIntegration(final @NotNull Application application) {
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
            "ActivityLifecycleIntegration enabled: %s",
            this.options.isEnableActivityLifecycleBreadcrumbs());

    performanceEnabled = isPerformanceEnabled(this.options);

    if (this.options.isEnableActivityLifecycleBreadcrumbs() || performanceEnabled) {
      application.registerActivityLifecycleCallbacks(this);
      options.getLogger().log(SentryLevel.DEBUG, "ActivityLifecycleIntegration installed.");
    }
  }

  private boolean isPerformanceEnabled(final @NotNull SentryAndroidOptions options) {
    return ((options.getTracesSampleRate() != null || options.getTracesSampler() != null)
        && options.isEnableAutoActivityLifecycleTracing());
  }

  @Override
  public void close() throws IOException {
    application.unregisterActivityLifecycleCallbacks(this);

    options.getLogger().log(SentryLevel.DEBUG, "ActivityLifecycleIntegration removed.");
  }

  private void addBreadcrumb(final @NonNull Activity activity, final @NotNull String state) {
    if (options.isEnableActivityLifecycleBreadcrumbs()) {
      final Breadcrumb breadcrumb = new Breadcrumb();
      breadcrumb.setType("navigation");
      breadcrumb.setData("state", state);
      breadcrumb.setData("screen", getActivityName(activity));
      breadcrumb.setCategory("ui.lifecycle");
      breadcrumb.setLevel(SentryLevel.INFO);
      hub.addBreadcrumb(breadcrumb);
    }
  }

  private @NotNull String getActivityName(final @NonNull Activity activity) {
    return activity.getClass().getSimpleName();
  }

  private void startTracing(final @NonNull Activity activity) {
    if (performanceEnabled && !isRunningTransaction(activity)) {
      final ITransaction transaction =
          hub.startTransaction(getActivityName(activity), "navigation");

      // lets bind to the scope so other integrations can pick it up
      hub.configureScope(
          scope -> {
            // we we'd not like to overwrite existent transactions bound to the Scope manually.
            if (scope.getTransaction() == null) {
              scope.setTransaction(transaction);
            }
          });

      activities.put(activity, transaction);
    }
  }

  private boolean isRunningTransaction(final @NonNull Activity activity) {
    return activities.containsKey(activity);
  }

  private void stopTracing(
      final @NonNull Activity activity, final boolean enableAutoActivityLifecycleTracingFinish) {
    if (performanceEnabled && enableAutoActivityLifecycleTracingFinish) {
      final ITransaction transaction = activities.get(activity);
      if (transaction != null && !transaction.isFinished()) {
        SpanStatus status = transaction.getStatus();
        // status might be set by other integrations, let's not overwrite it
        if (status == null) {
          status = SpanStatus.OK;
        }
        transaction.finish(status);
      }
    }
  }

  @Override
  public synchronized void onActivityCreated(
      final @NonNull Activity activity, final @Nullable Bundle savedInstanceState) {
    addBreadcrumb(activity, "created");

    // if activity has global fields being init. and
    // they are slow, this won't count the whole fields/ctor initialization time, but only
    // when onCreate is actually called.
    startTracing(activity);
  }

  @Override
  public synchronized void onActivityStarted(final @NonNull Activity activity) {
    addBreadcrumb(activity, "started");
  }

  @Override
  public synchronized void onActivityResumed(final @NonNull Activity activity) {
    addBreadcrumb(activity, "resumed");
  }

  @Override
  public synchronized void onActivityPostResumed(final @NonNull Activity activity) {
    // this should be called only when onResume has been executed already, which means
    // the UI is responsive at this moment.
    stopTracing(activity, options.isEnableAutoActivityLifecycleTracingFinish());
  }

  @Override
  public synchronized void onActivityPaused(final @NonNull Activity activity) {
    addBreadcrumb(activity, "paused");
  }

  @Override
  public synchronized void onActivityStopped(final @NonNull Activity activity) {
    addBreadcrumb(activity, "stopped");

    // clear up so we dont start again for the same activity if the activity is in the acitvity
    // stack still
    // if the activity is opened again and not in memory, transactions will be created normally.
    if (performanceEnabled) {
      activities.remove(activity);
    }
  }

  @Override
  public synchronized void onActivitySaveInstanceState(
      final @NonNull Activity activity, final @NonNull Bundle outState) {
    addBreadcrumb(activity, "saveInstanceState");
  }

  @Override
  public synchronized void onActivityDestroyed(final @NonNull Activity activity) {
    addBreadcrumb(activity, "destroyed");
  }

  @Override
  public synchronized void onActivityPostDestroyed(final @NonNull Activity activity) {
    // in case people opt-out enableAutoActivityLifecycleTracingFinish and forgot to finish it,
    // we do it automatically before moving to a new Activity.
    stopTracing(activity, true);
  }
}
