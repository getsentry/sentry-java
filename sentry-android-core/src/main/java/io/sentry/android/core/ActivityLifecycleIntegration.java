package io.sentry.android.core;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Integration;
import io.sentry.Scope;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

public final class ActivityLifecycleIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  private static final String NAV_OP = "navigation";

  private final @NotNull Application application;
  private @Nullable IHub hub;
  private @Nullable SentryAndroidOptions options;

  private boolean performanceEnabled = false;

  private boolean isAllActivityCallbacksAvailable;

  private boolean firstActivityCreated = false;
  private boolean firstActivityResumed = false;

  private @Nullable ISpan appStartSpan;

  // WeakHashMap isn't thread safe but ActivityLifecycleCallbacks is only called from the
  // main-thread
  private final @NotNull WeakHashMap<Activity, ITransaction> activitiesWithOngoingTransactions =
      new WeakHashMap<>();

  public ActivityLifecycleIntegration(
      final @NotNull Application application, final @NotNull IBuildInfoProvider buildInfoProvider) {
    this.application = Objects.requireNonNull(application, "Application is required");
    Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");

    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.Q) {
      isAllActivityCallbacksAvailable = true;
    }
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
      this.options.getLogger().log(SentryLevel.DEBUG, "ActivityLifecycleIntegration installed.");
    }
  }

  private boolean isPerformanceEnabled(final @NotNull SentryAndroidOptions options) {
    return options.isTracingEnabled() && options.isEnableAutoActivityLifecycleTracing();
  }

  @Override
  public void close() throws IOException {
    application.unregisterActivityLifecycleCallbacks(this);

    if (options != null) {
      options.getLogger().log(SentryLevel.DEBUG, "ActivityLifecycleIntegration removed.");
    }
  }

  private void addBreadcrumb(final @NonNull Activity activity, final @NotNull String state) {
    if (options != null && hub != null && options.isEnableActivityLifecycleBreadcrumbs()) {
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

  private void stopPreviousTransactions() {
    for (final Map.Entry<Activity, ITransaction> entry :
        activitiesWithOngoingTransactions.entrySet()) {
      final ITransaction transaction = entry.getValue();
      finishTransaction(transaction);
    }
  }

  private void startTracing(final @NonNull Activity activity) {
    if (performanceEnabled && !isRunningTransaction(activity) && hub != null) {
      // as we allow a single transaction running on the bound Scope, we finish the previous ones
      stopPreviousTransactions();

      // we can only bind to the scope if there's no running transaction
      ITransaction transaction;
      final String activityName = getActivityName(activity);

      final Date appStartTime = AppStartState.getInstance().getAppStartTime();

      // in case appStartTime isn't available, we don't create a span for it.
      if (firstActivityCreated || appStartTime == null) {
        transaction = hub.startTransaction(activityName, NAV_OP);
      } else {
        // start transaction with app start timestamp
        transaction = hub.startTransaction(activityName, NAV_OP, appStartTime);
        // start specific span for app start
        // TODO: add description cold/warm or different operation? how do we break down
        // per app start cold/warm?
        appStartSpan = transaction.startChild("app.start", appStartTime);
      }

      // lets bind to the scope so other integrations can pick it up
      hub.configureScope(
          scope -> {
            applyScope(scope, transaction);
          });

      activitiesWithOngoingTransactions.put(activity, transaction);
    }
  }

  @VisibleForTesting
  void applyScope(final @NotNull Scope scope, final @NotNull ITransaction transaction) {
    scope.withTransaction(
        scopeTransaction -> {
          // we'd not like to overwrite existent transactions bound to the Scope
          // manually.
          if (scopeTransaction == null) {
            scope.setTransaction(transaction);
          } else if (options != null) {
            options
                .getLogger()
                .log(
                    SentryLevel.DEBUG,
                    "Transaction '%s' won't be bound to the Scope since there's one already in there.",
                    transaction.getName());
          }
        });
  }

  private boolean isRunningTransaction(final @NonNull Activity activity) {
    return activitiesWithOngoingTransactions.containsKey(activity);
  }

  private void stopTracing(final @NonNull Activity activity, final boolean shouldFinishTracing) {
    if (performanceEnabled && shouldFinishTracing) {
      final ITransaction transaction = activitiesWithOngoingTransactions.get(activity);
      finishTransaction(transaction);
    }
  }

  private void finishTransaction(final @Nullable ITransaction transaction) {
    if (transaction != null) {
      SpanStatus status = transaction.getStatus();
      // status might be set by other integrations, let's not overwrite it
      if (status == null) {
        status = SpanStatus.OK;
      }
      transaction.finish(status);
    }
  }

  @Override
  public synchronized void onActivityPreCreated(
      final @NonNull Activity activity, final @Nullable Bundle savedInstanceState) {

    // only executed if API >= 29 otherwise it happens on onActivityCreated
    if (isAllActivityCallbacksAvailable) {
      // if activity has global fields being init. and
      // they are slow, this won't count the whole fields/ctor initialization time, but only
      // when onCreate is actually called.
      startTracing(activity);
    }
  }

  @Override
  public synchronized void onActivityCreated(
      final @NonNull Activity activity, final @Nullable Bundle savedInstanceState) {
    if (!firstActivityCreated && performanceEnabled) {
      // if Activity has savedInstanceState then its a warm start
      // https://developer.android.com/topic/performance/vitals/launch-time#warm
      AppStartState.getInstance().setColdStart(savedInstanceState == null);
    }

    addBreadcrumb(activity, "created");

    // fallback call for API < 29 compatibility, otherwise it happens on onActivityPreCreated
    if (!isAllActivityCallbacksAvailable) {
      startTracing(activity);
    }
    firstActivityCreated = true;
  }

  @Override
  public synchronized void onActivityStarted(final @NonNull Activity activity) {
    addBreadcrumb(activity, "started");
  }

  @Override
  public synchronized void onActivityResumed(final @NonNull Activity activity) {
    if (!firstActivityResumed && performanceEnabled) {
      // sets App start as finished when the very first activity calls onResume
      AppStartState.getInstance().setAppStartEnd();

      // finishes app start span
      if (appStartSpan != null) {
        appStartSpan.finish();
      }
      firstActivityResumed = true;
    }

    addBreadcrumb(activity, "resumed");

    // fallback call for API < 29 compatibility, otherwise it happens on onActivityPostResumed
    if (!isAllActivityCallbacksAvailable && options != null) {
      stopTracing(activity, options.isEnableActivityLifecycleTracingAutoFinish());
    }
  }

  @Override
  public synchronized void onActivityPostResumed(final @NonNull Activity activity) {
    // only executed if API >= 29 otherwise it happens on onActivityResumed
    if (isAllActivityCallbacksAvailable && options != null) {
      // this should be called only when onResume has been executed already, which means
      // the UI is responsive at this moment.
      stopTracing(activity, options.isEnableActivityLifecycleTracingAutoFinish());
    }
  }

  @Override
  public synchronized void onActivityPaused(final @NonNull Activity activity) {
    addBreadcrumb(activity, "paused");
  }

  @Override
  public synchronized void onActivityStopped(final @NonNull Activity activity) {
    addBreadcrumb(activity, "stopped");
  }

  @Override
  public synchronized void onActivitySaveInstanceState(
      final @NonNull Activity activity, final @NonNull Bundle outState) {
    addBreadcrumb(activity, "saveInstanceState");
  }

  @Override
  public synchronized void onActivityDestroyed(final @NonNull Activity activity) {
    addBreadcrumb(activity, "destroyed");

    // in case people opt-out enableActivityLifecycleTracingAutoFinish and forgot to finish it,
    // we make sure to finish it when the activity gets destroyed.
    stopTracing(activity, true);

    // clear it up, so we don't start again for the same activity if the activity is in the activity
    // stack still.
    // if the activity is opened again and not in memory, transactions will be created normally.
    if (performanceEnabled) {
      activitiesWithOngoingTransactions.remove(activity);
    }
  }

  @TestOnly
  @NotNull
  WeakHashMap<Activity, ITransaction> getActivitiesWithOngoingTransactions() {
    return activitiesWithOngoingTransactions;
  }
}
