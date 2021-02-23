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

  //  private @NotNull final AtomicBoolean coldStart = new AtomicBoolean(true);

  // TODO maybe use a Set?
  // TODO make it thread safe?
  private final WeakHashMap<Activity, ITransaction> activities = new WeakHashMap<>();

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
            "ActivityBreadcrumbsIntegration enabled: %s",
            this.options.isEnableActivityLifecycleBreadcrumbs());

    if (this.options.isEnableActivityLifecycleBreadcrumbs()
        || this.options.isEnableActivityLifecycleTracing()) {
      application.registerActivityLifecycleCallbacks(this);
      options.getLogger().log(SentryLevel.DEBUG, "ActivityBreadcrumbsIntegration installed.");
    }
  }

  @Override
  public void close() throws IOException {
    application.unregisterActivityLifecycleCallbacks(this);

    options.getLogger().log(SentryLevel.DEBUG, "ActivityBreadcrumbsIntegration removed.");
  }

  private void addBreadcrumb(final @NonNull Activity activity, final @NotNull String state) {
    if (options.isEnableActivityLifecycleBreadcrumbs()) {
      final Breadcrumb breadcrumb = new Breadcrumb();
      breadcrumb.setType("navigation");
      breadcrumb.setData("state", state);
      breadcrumb.setData("screen", activity.getClass().getSimpleName());
      breadcrumb.setCategory("ui.lifecycle");
      breadcrumb.setLevel(SentryLevel.INFO);
      hub.addBreadcrumb(breadcrumb);
    }
  }

  private void startTracing(@NonNull Activity activity) {
    if (options.isEnableActivityLifecycleTracing() && !isRunningTransaction(activity)) {
      final ITransaction transaction = hub.startTransaction("ActivityStart", "navigation");

      // hot // cold // warm, how do we know?
      //      final String state = coldStart.get() ? "cold" : "warm";

      //      transaction.setTag("state", state);
      transaction.setDescription(activity.getClass().getSimpleName());

      // lets bind to the scope so we integrations can pick it up
      // TODO: check if theres not one already running?
      hub.configureScope(scope -> scope.setTransaction(transaction));

      activities.put(activity, transaction);
    }
  }

  private boolean isRunningTransaction(@NonNull Activity activity) {
    return activities.containsKey(activity);
  }

  private void stopTracing(@NonNull Activity activity) {
    final ITransaction transaction = activities.get(activity);
    if (options.isEnableActivityLifecycleTracing() && transaction != null) {

      SpanStatus status = transaction.getStatus();
      // status might be set by other integrations, let's not overwrite it
      if (status == null) {
        status = SpanStatus.OK;
      }
      transaction.finish(status);
    }
  }

  @Override
  public synchronized void onActivityCreated(
      @NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    addBreadcrumb(activity, "created");

    // if activity has global fields being init. and
    // they are slow, this won't take the whole fields initialization time
    startTracing(activity);
    // if its cold start (1st activity within App's lifecycle, set it to false now)
    //    coldStart.compareAndSet(true, false);

    // we could create spans out of these methods, before/after loading like
    // onCreate span, onStarted span, onResume span
    // or users do themselves
  }

  @Override
  public synchronized void onActivityStarted(@NonNull Activity activity) {
    addBreadcrumb(activity, "started");
  }

  @Override
  public synchronized void onActivityResumed(@NonNull Activity activity) {
    addBreadcrumb(activity, "resumed");
  }

  @Override
  public synchronized void onActivityPostResumed(@NonNull Activity activity) {
    // this should be replaced by idle transactions
    // here probably we need to take the timestamp if no other spans happen to finish the
    // transaction

    // this should be called only when we know onresumed has been executed already,
    // right now this is executed before the onresume, we need to force it
    stopTracing(activity);
  }

  @Override
  public synchronized void onActivityPaused(@NonNull Activity activity) {
    addBreadcrumb(activity, "paused");

    // we dont clean up here because the next run would start a new transaction for the same
    // activity
    // but then it'd be a hot reload, or should we have transactions for hot and cold loading?
  }

  @Override
  public synchronized void onActivityStopped(@NonNull Activity activity) {
    addBreadcrumb(activity, "stopped");

    // this should be replaced by idle transactions
    // stopTracing(); only here if we'd like to track from beginning to the end

    // clear up so we dont start again for the same activity
    // TODO: should we do this on onActivityDestroyed?
    if (options.isEnableActivityLifecycleTracing()) {
      activities.remove(activity);
    }
  }

  @Override
  public synchronized void onActivitySaveInstanceState(
      @NonNull Activity activity, @NonNull Bundle outState) {
    addBreadcrumb(activity, "saveInstanceState");
  }

  @Override
  public synchronized void onActivityDestroyed(@NonNull Activity activity) {
    addBreadcrumb(activity, "destroyed");
  }
}
