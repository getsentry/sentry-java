package io.sentry.android.core;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.SparseIntArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.FrameMetricsAggregator;
import io.sentry.Breadcrumb;
import io.sentry.DateUtils;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.Integration;
import io.sentry.Scope;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SentryTracer;
import io.sentry.SpanStatus;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryTransaction;
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

  private final @NotNull Application application;
  private @NotNull IHub hub;
  private @NotNull SentryAndroidOptions options;

  private boolean performanceEnabled = false;

  private boolean isAllActivityCallbacksAvailable;

  // does it need to be atomic? its only in the main thread
  private boolean firstActivityCreated = false;
  private boolean hasSavedState = false;
  //  private boolean sentAppStart = false;

  //  private @NotNull final Date appStartTime;

  // or FrameMetrics but only >= API 24 android 7
  // androidx already uses FrameMetrics and onFrameMetricsAvailable internally if API 24
  // so nice abstraction for free but gets the dependency too
  private final FrameMetricsAggregator frameMetricsAggregator = new FrameMetricsAggregator();

  // WeakHashMap isn't thread safe but ActivityLifecycleCallbacks is only called from the
  // main-thread
  private final @NotNull WeakHashMap<Activity, ITransaction> activitiesWithOngoingTransactions =
      new WeakHashMap<>();

  // could also use a ArrayList<WeakReference<Activity>> if creating transaction at the end only
  private final @NotNull WeakHashMap<Activity, ITransaction>
      activitiesWithFramesRatesOngoingTransactions = new WeakHashMap<>();

  //  private @Nullable Date appStartTimeEnd;

  // TODO: on Android we should use SystemClock.uptimeMillis() for intervals

  public ActivityLifecycleIntegration(
      final @NotNull Application application, final @NotNull IBuildInfoProvider buildInfoProvider) {
    this.application = Objects.requireNonNull(application, "Application is required");
    Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
    //    this.appStartTime = appStartTime;

    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.Q) {
      isAllActivityCallbacksAvailable = true;
    }
  }

  @SuppressWarnings("deprecation")
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

      // Handler deprecated
      new Handler()
          .post(
              () -> {
                if (firstActivityCreated) {
                  if (hasSavedState) {
                    AppStartUpState.getInstance().setColdStartUp(false);
                  } else {
                    AppStartUpState.getInstance().setColdStartUp(true);
                  }
                }
              });
    }
  }

  private boolean isPerformanceEnabled(final @NotNull SentryAndroidOptions options) {
    return options.isTracingEnabled() && options.isEnableAutoActivityLifecycleTracing();
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

  private void stopPreviousTransactions() {
    for (final Map.Entry<Activity, ITransaction> entry :
        activitiesWithOngoingTransactions.entrySet()) {
      final ITransaction transaction = entry.getValue();
      finishTransaction(transaction);
    }
  }

  private void startTracing(final @NonNull Activity activity) {
    if (performanceEnabled && !isRunningTransaction(activity)) {
      // as we allow a single transaction running on the bound Scope, we finish the previous ones
      stopPreviousTransactions();

      // we can only bind to the scope if there's no running transaction
      final ITransaction transaction =
          hub.startTransaction(getActivityName(activity), "navigation");

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
          } else {
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
    addBreadcrumb(activity, "created");

    // fallback call for API < 29 compatibility, otherwise it happens on onActivityPreCreated
    if (!isAllActivityCallbacksAvailable) {
      startTracing(activity);
    }

    //    if (!sentAppStart) {
    if (firstActivityCreated) {
      return;
    }
    firstActivityCreated = true;
    hasSavedState = savedInstanceState != null;
    //    }
  }

  @Override
  public synchronized void onActivityStarted(final @NonNull Activity activity) {
    addBreadcrumb(activity, "started");

    if (!activitiesWithFramesRatesOngoingTransactions.containsKey(activity)) {
      // probably need to check if it already exists with another map
      frameMetricsAggregator.add(activity);
      final ITransaction transaction =
          hub.startTransaction(getActivityName(activity), "frames_metrics");
      activitiesWithFramesRatesOngoingTransactions.put(activity, transaction);
    }
  }

  //  private boolean isHardwareAccelerated(Activity activity) {
  //    // we can't observe frame rates for a non hardware accelerated view
  //    return activity.getWindow() != null
  //        && ((activity.getWindow().getAttributes().flags
  //                & WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
  //            != 0);
  //  }

  @Override
  public synchronized void onActivityResumed(final @NonNull Activity activity) {
    //    if (!sentAppStart) {
    //      if (!firstActivityCreated) {
    //      isAppStartUp = true;
    //      transactionAppStartUp(DateUtils.getCurrentDateTime());
    //        appStartTimeEnd = DateUtils.getCurrentDateTime();
    //      }
    //    }
    if (!AppStartUpState.getInstance().isSentStartUp()) {
      long millis = SystemClock.uptimeMillis();
      AppStartUpState.getInstance().setAppStartUpEnd(millis);
    }

    addBreadcrumb(activity, "resumed");

    // fallback call for API < 29 compatibility, otherwise it happens on onActivityPostResumed
    if (!isAllActivityCallbacksAvailable) {
      stopTracing(activity, options.isEnableActivityLifecycleTracingAutoFinish());
    }
  }

  @Override
  public synchronized void onActivityPostResumed(final @NonNull Activity activity) {
    // only executed if API >= 29 otherwise it happens on onActivityResumed
    if (isAllActivityCallbacksAvailable) {
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

    if (activitiesWithFramesRatesOngoingTransactions.containsKey(activity)) {
      final Date timestamp = DateUtils.getCurrentDateTime();
      final ITransaction transaction = activitiesWithFramesRatesOngoingTransactions.get(activity);
      activitiesWithFramesRatesOngoingTransactions.remove(activity);

      if (transaction != null) {
        int totalFrames = 0;
        int slowFrames = 0;
        int frozenFrames = 0;
        // Stops recording metrics for this Activity and returns the currently-collected metrics
        final SparseIntArray[] framesRates = frameMetricsAggregator.remove(activity);
        if (framesRates != null) {
          final SparseIntArray totalIndexArray = framesRates[FrameMetricsAggregator.TOTAL_INDEX];
          if (totalIndexArray != null) {
            for (int i = 0; i < totalIndexArray.size(); i++) {
              int frameTime = totalIndexArray.keyAt(i);
              int numFrames = totalIndexArray.valueAt(i);
              totalFrames += numFrames;
              // hard coded values, its also in the official android docs and frame metrics API
              if (frameTime > 700) {
                // frozen frames, threshold is 700ms
                frozenFrames += numFrames;
              }
              if (frameTime > 16) {
                // slow frames, above 16ms, 60 frames/second
                slowFrames += numFrames;
              }
            }
          }
        }

        if (totalFrames == 0 && slowFrames == 0 && frozenFrames == 0) {
          return;
        }

        // find a way to attach metrics in a better way
        SentryTracer tracer = (SentryTracer) transaction;
        tracer.setStatus(SpanStatus.OK);
        SentryTransaction newTransaction =
            new SentryTransaction(tracer, tracer.getStartTimestamp(), timestamp);

        // we could calculate locally too
        MeasurementValue tfValues = new MeasurementValue(totalFrames);
        MeasurementValue sfValues = new MeasurementValue(slowFrames);
        MeasurementValue ffValues = new MeasurementValue(frozenFrames);
        newTransaction.getMeasurements().put("total_frames", tfValues);
        newTransaction.getMeasurements().put("slow_frames", sfValues);
        newTransaction.getMeasurements().put("frozen_frames", ffValues);

        hub.captureTransaction(newTransaction);
      }
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

  //  @SuppressWarnings("JavaUtilDate")
  //  private void transactionAppStartUp(final boolean isColdStart) {
  //    if (appStartTimeEnd == null) {
  //      return;
  //    }

  // there should be an easier and public/internal way to start/end a transaction
  // with given time stamps

  //    final String op = isColdStart ? "cold" : "warm";
  //    TransactionContext transactionContext = new TransactionContext("app_start_time", op);
  //    transactionContext.setSampled(true);
  //
  //    SentryTracer tracer = new SentryTracer(transactionContext, hub);
  //    SentryTransaction transaction = new SentryTransaction(tracer, appStartTime,
  // appStartTimeEnd);

  // backend could also calculate that
  //    final long totalMillis = appStartTimeEnd.getTime() - appStartTime.getTime();
  //    MeasurementValue value = new MeasurementValue((float) totalMillis);
  //    transaction.getMeasurements().put("app_start_time", value);
  //
  //    tracer.setStatus(SpanStatus.OK);
  //
  //    hub.captureTransaction(transaction);
  //  }
}
