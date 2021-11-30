package io.sentry.android.core;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
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
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

public final class ActivityLifecycleIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  static final String UI_LOAD_OP = "ui.load";
  static final String APP_START_WARM = "app.start.warm";
  static final String APP_START_COLD = "app.start.cold";

  private final @NotNull Application application;
  private @Nullable IHub hub;
  private @Nullable SentryAndroidOptions options;

  private boolean performanceEnabled = false;

  private boolean isAllActivityCallbacksAvailable;

  private boolean firstActivityCreated = false;
  private boolean firstActivityResumed = false;
  private boolean foregroundImportance = false;

  private @Nullable ISpan appStartSpan;

  // WeakHashMap isn't thread safe but ActivityLifecycleCallbacks is only called from the
  // main-thread
  private final @NotNull WeakHashMap<Activity, ITransaction> activitiesWithOngoingTransactions =
      new WeakHashMap<>();

  private final @NotNull ActivityFramesTracker activityFramesTracker;

  public ActivityLifecycleIntegration(
      final @NotNull Application application,
      final @NotNull IBuildInfoProvider buildInfoProvider,
      final @NotNull ActivityFramesTracker activityFramesTracker) {
    this.application = Objects.requireNonNull(application, "Application is required");
    Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
    this.activityFramesTracker =
        Objects.requireNonNull(activityFramesTracker, "ActivityFramesTracker is required");

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

    activityFramesTracker.stop();
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

      final Date appStartTime =
          foregroundImportance ? AppStartState.getInstance().getAppStartTime() : null;

      // in case appStartTime isn't available, we don't create a span for it.
      if (firstActivityCreated || appStartTime == null) {
        transaction =
            hub.startTransaction(
                activityName,
                UI_LOAD_OP,
                (Date) null,
                true,
                (finishingTransaction) -> {
                  activityFramesTracker.setMetrics(activity, finishingTransaction.getEventId());
                });
      } else {
        // start transaction with app start timestamp
        transaction =
            hub.startTransaction(
                activityName,
                UI_LOAD_OP,
                appStartTime,
                true,
                (finishingTransaction) -> {
                  activityFramesTracker.setMetrics(activity, finishingTransaction.getEventId());
                });
        // start specific span for app start

        appStartSpan = transaction.startChild(getAppStartOp(), getAppStartDesc(), appStartTime);
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
      // if io.sentry.traces.activity.auto-finish.enable is disabled, transaction may be already
      // finished manually when this method is called.
      if (transaction.isFinished()) {
        return;
      }

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
      final @NonNull Activity activity, final @Nullable Bundle savedInstanceState) {
    if (!firstActivityCreated) {
      // we only track app start for processes that will show an Activity (full launch).
      // Here we check the process importance which will tell us that.
      foregroundImportance = isForegroundImportance();
    }

    setColdStart(savedInstanceState);
    addBreadcrumb(activity, "created");
    startTracing(activity);

    firstActivityCreated = true;
  }

  @Override
  public synchronized void onActivityStarted(final @NonNull Activity activity) {
    // The docs on the screen rendering performance tracing
    // (https://firebase.google.com/docs/perf-mon/screen-traces?platform=android#definition),
    // state that the tracing starts for every Activity class when the app calls .onActivityStarted.
    // Adding an Activity in onActivityCreated leads to Window.FEATURE_NO_TITLE not
    // working. Moving this to onActivityStarted fixes the problem.
    activityFramesTracker.addActivity(activity);

    addBreadcrumb(activity, "started");
  }

  @Override
  public synchronized void onActivityResumed(final @NonNull Activity activity) {
    if (!firstActivityResumed && performanceEnabled) {

      // we only finish the app start if the process is of foregroundImportance
      if (foregroundImportance) {
        // sets App start as finished when the very first activity calls onResume
        AppStartState.getInstance().setAppStartEnd();
      }

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

    // in case the appStartSpan isn't completed yet, we finish it as cancelled to avoid
    // memory leak
    if (appStartSpan != null && !appStartSpan.isFinished()) {
      appStartSpan.finish(SpanStatus.CANCELLED);
    }

    // in case people opt-out enableActivityLifecycleTracingAutoFinish and forgot to finish it,
    // we make sure to finish it when the activity gets destroyed.
    stopTracing(activity, true);

    // set it to null in case its been just finished as cancelled
    appStartSpan = null;

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

  @TestOnly
  @Nullable
  ISpan getAppStartSpan() {
    return appStartSpan;
  }

  private void setColdStart(final @Nullable Bundle savedInstanceState) {
    if (!firstActivityCreated && performanceEnabled) {
      // if Activity has savedInstanceState then its a warm start
      // https://developer.android.com/topic/performance/vitals/launch-time#warm
      AppStartState.getInstance().setColdStart(savedInstanceState == null);
    }
  }

  private @NotNull String getAppStartDesc() {
    if (AppStartState.getInstance().isColdStart()) {
      return "Cold Start";
    } else {
      return "Warm Start";
    }
  }

  private @NotNull String getAppStartOp() {
    if (AppStartState.getInstance().isColdStart()) {
      return APP_START_COLD;
    } else {
      return APP_START_WARM;
    }
  }

  /**
   * Check if the Started process has IMPORTANCE_FOREGROUND importance which means that the process
   * will start an Activity.
   *
   * @return true if IMPORTANCE_FOREGROUND and false otherwise
   */
  private boolean isForegroundImportance() {
    try {
      final Object service = application.getSystemService(Context.ACTIVITY_SERVICE);
      if (service instanceof ActivityManager) {
        final ActivityManager activityManager = (ActivityManager) service;
        final List<ActivityManager.RunningAppProcessInfo> runningAppProcesses =
            activityManager.getRunningAppProcesses();

        if (runningAppProcesses != null) {
          final int myPid = Process.myPid();
          for (final ActivityManager.RunningAppProcessInfo processInfo : runningAppProcesses) {
            if (processInfo.pid == myPid) {
              if (processInfo.importance == IMPORTANCE_FOREGROUND) {
                return true;
              }
              break;
            }
          }
        }
      }
    } catch (SecurityException ignored) {
      // happens for isolated processes
    } catch (Throwable ignored) {
      // should never happen
    }
    return false;
  }
}
