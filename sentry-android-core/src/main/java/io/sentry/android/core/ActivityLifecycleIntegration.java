package io.sentry.android.core;

import static io.sentry.MeasurementUnit.Duration.MILLISECOND;
import static io.sentry.TypeCheckHint.ANDROID_ACTIVITY;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.annotation.NonNull;
import io.sentry.Breadcrumb;
import io.sentry.FullyDisplayedReporter;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Instrumenter;
import io.sentry.Integration;
import io.sentry.Scope;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.android.core.internal.util.FirstDrawDoneListener;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

public final class ActivityLifecycleIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  static final String UI_LOAD_OP = "ui.load";
  static final String APP_START_WARM = "app.start.warm";
  static final String APP_START_COLD = "app.start.cold";
  static final String TTID_OP = "ui.load.initial_display";
  static final String TTFD_OP = "ui.load.full_display";
  static final long TTFD_TIMEOUT_MILLIS = 30000;

  private final @NotNull Application application;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private @Nullable IHub hub;
  private @Nullable SentryAndroidOptions options;

  private boolean performanceEnabled = false;

  private boolean timeToFullDisplaySpanEnabled = false;

  private boolean isAllActivityCallbacksAvailable;

  private boolean firstActivityCreated = false;
  private final boolean foregroundImportance;

  private @Nullable FullyDisplayedReporter fullyDisplayedReporter = null;
  private @Nullable ISpan appStartSpan;
  private final @NotNull WeakHashMap<Activity, ISpan> ttidSpanMap = new WeakHashMap<>();
  private final @NotNull WeakHashMap<Activity, ISpan> ttfdSpanMap = new WeakHashMap<>();
  private @NotNull SentryDate lastPausedTime = AndroidDateUtils.getCurrentSentryDateTime();
  private final @NotNull Handler mainHandler = new Handler(Looper.getMainLooper());
  private @Nullable Future<?> ttfdAutoCloseFuture = null;

  // WeakHashMap isn't thread safe but ActivityLifecycleCallbacks is only called from the
  // main-thread
  private final @NotNull WeakHashMap<Activity, ITransaction> activitiesWithOngoingTransactions =
      new WeakHashMap<>();

  private final @NotNull ActivityFramesTracker activityFramesTracker;

  public ActivityLifecycleIntegration(
      final @NotNull Application application,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull ActivityFramesTracker activityFramesTracker) {
    this.application = Objects.requireNonNull(application, "Application is required");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
    this.activityFramesTracker =
        Objects.requireNonNull(activityFramesTracker, "ActivityFramesTracker is required");

    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.Q) {
      isAllActivityCallbacksAvailable = true;
    }

    // we only track app start for processes that will show an Activity (full launch).
    // Here we check the process importance which will tell us that.
    foregroundImportance = ContextUtils.isForegroundImportance(this.application);
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
    fullyDisplayedReporter = this.options.getFullyDisplayedReporter();
    timeToFullDisplaySpanEnabled = this.options.isEnableTimeToFullDisplayTracing();

    if (this.options.isEnableActivityLifecycleBreadcrumbs() || performanceEnabled) {
      application.registerActivityLifecycleCallbacks(this);
      this.options.getLogger().log(SentryLevel.DEBUG, "ActivityLifecycleIntegration installed.");
      addIntegrationToSdkVersion();
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

  private void addBreadcrumb(final @NotNull Activity activity, final @NotNull String state) {
    if (options != null && hub != null && options.isEnableActivityLifecycleBreadcrumbs()) {
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
  }

  private @NotNull String getActivityName(final @NotNull Activity activity) {
    return activity.getClass().getSimpleName();
  }

  private void stopPreviousTransactions() {
    for (final Map.Entry<Activity, ITransaction> entry :
        activitiesWithOngoingTransactions.entrySet()) {
      final ITransaction transaction = entry.getValue();
      final ISpan ttidSpan = ttidSpanMap.get(entry.getKey());
      final ISpan ttfdSpan = ttfdSpanMap.get(entry.getKey());
      finishTransaction(transaction, ttidSpan, ttfdSpan);
    }
  }

  private void startTracing(final @NotNull Activity activity) {
    WeakReference<Activity> weakActivity = new WeakReference<>(activity);
    if (performanceEnabled && !isRunningTransaction(activity) && hub != null) {
      // as we allow a single transaction running on the bound Scope, we finish the previous ones
      stopPreviousTransactions();

      final String activityName = getActivityName(activity);

      final SentryDate appStartTime =
          foregroundImportance ? AppStartState.getInstance().getAppStartTime() : null;
      final Boolean coldStart = AppStartState.getInstance().isColdStart();

      final TransactionOptions transactionOptions = new TransactionOptions();
      if (options.isEnableActivityLifecycleTracingAutoFinish()) {
        transactionOptions.setIdleTimeout(options.getIdleTimeout());
        transactionOptions.setTrimEnd(true);
      }
      transactionOptions.setWaitForChildren(true);
      transactionOptions.setTransactionFinishedCallback(
          (finishingTransaction) -> {
            @Nullable Activity unwrappedActivity = weakActivity.get();
            if (unwrappedActivity != null) {
              activityFramesTracker.setMetrics(
                  unwrappedActivity, finishingTransaction.getEventId());
            } else {
              if (options != null) {
                options
                    .getLogger()
                    .log(
                        SentryLevel.WARNING,
                        "Unable to track activity frames as the Activity %s has been destroyed.",
                        activityName);
              }
            }
          });

      // This will be the start timestamp of the transaction, as well as the ttid/ttfd spans
      final @NotNull SentryDate ttidStartTime;

      if (!(firstActivityCreated || appStartTime == null || coldStart == null)) {
        // The first activity ttid/ttfd spans should start at the app start time
        ttidStartTime = appStartTime;
      } else {
        // The ttid/ttfd spans should start when the previous activity called its onPause method
        ttidStartTime = lastPausedTime;
      }
      transactionOptions.setStartTimestamp(ttidStartTime);

      // we can only bind to the scope if there's no running transaction
      ITransaction transaction =
          hub.startTransaction(
              new TransactionContext(activityName, TransactionNameSource.COMPONENT, UI_LOAD_OP),
              transactionOptions);
      setSpanOrigin(transaction);

      // in case appStartTime isn't available, we don't create a span for it.
      if (!(firstActivityCreated || appStartTime == null || coldStart == null)) {
        // start specific span for app start
        appStartSpan =
            transaction.startChild(
                getAppStartOp(coldStart),
                getAppStartDesc(coldStart),
                appStartTime,
                Instrumenter.SENTRY);
        setSpanOrigin(appStartSpan);

        // in case there's already an end time (e.g. due to deferred SDK init)
        // we can finish the app-start span
        finishAppStartSpan();
      }
      final @NotNull ISpan ttidSpan =
          transaction.startChild(
              TTID_OP, getTtidDesc(activityName), ttidStartTime, Instrumenter.SENTRY);
      ttidSpanMap.put(activity, ttidSpan);
      setSpanOrigin(ttidSpan);

      if (timeToFullDisplaySpanEnabled && fullyDisplayedReporter != null && options != null) {
        final @NotNull ISpan ttfdSpan =
            transaction.startChild(
                TTFD_OP, getTtfdDesc(activityName), ttidStartTime, Instrumenter.SENTRY);
        setSpanOrigin(ttfdSpan);
        try {
          ttfdSpanMap.put(activity, ttfdSpan);
          ttfdAutoCloseFuture =
              options
                  .getExecutorService()
                  .schedule(() -> finishExceededTtfdSpan(ttfdSpan, ttidSpan), TTFD_TIMEOUT_MILLIS);
        } catch (RejectedExecutionException e) {
          options
              .getLogger()
              .log(
                  SentryLevel.ERROR,
                  "Failed to call the executor. Time to full display span will not be finished automatically. Did you call Sentry.close()?",
                  e);
        }
      }

      // lets bind to the scope so other integrations can pick it up
      hub.configureScope(
          scope -> {
            applyScope(scope, transaction);
          });

      activitiesWithOngoingTransactions.put(activity, transaction);
    }
  }

  private void setSpanOrigin(ISpan span) {
    if (span != null) {
      span.getSpanContext().setOrigin("auto.ui.activity");
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

  @VisibleForTesting
  void clearScope(final @NotNull Scope scope, final @NotNull ITransaction transaction) {
    scope.withTransaction(
        scopeTransaction -> {
          if (scopeTransaction == transaction) {
            scope.clearTransaction();
          }
        });
  }

  private boolean isRunningTransaction(final @NotNull Activity activity) {
    return activitiesWithOngoingTransactions.containsKey(activity);
  }

  private void stopTracing(final @NotNull Activity activity, final boolean shouldFinishTracing) {
    if (performanceEnabled && shouldFinishTracing) {
      final ITransaction transaction = activitiesWithOngoingTransactions.get(activity);
      finishTransaction(transaction, null, null);
    }
  }

  private void finishTransaction(
      final @Nullable ITransaction transaction,
      final @Nullable ISpan ttidSpan,
      final @Nullable ISpan ttfdSpan) {
    if (transaction != null) {
      // if io.sentry.traces.activity.auto-finish.enable is disabled, transaction may be already
      // finished manually when this method is called.
      if (transaction.isFinished()) {
        return;
      }

      // in case the ttidSpan isn't completed yet, we finish it as cancelled to avoid memory leak
      finishSpan(ttidSpan, SpanStatus.DEADLINE_EXCEEDED);
      finishExceededTtfdSpan(ttfdSpan, ttidSpan);
      cancelTtfdAutoClose();

      SpanStatus status = transaction.getStatus();
      // status might be set by other integrations, let's not overwrite it
      if (status == null) {
        status = SpanStatus.OK;
      }
      transaction.finish(status);
      if (hub != null) {
        // make sure to remove the transaction from scope, as it may contain running children,
        // therefore `finish` method will not remove it from scope
        hub.configureScope(
            scope -> {
              clearScope(scope, transaction);
            });
      }
    }
  }

  @Override
  public synchronized void onActivityCreated(
      final @NotNull Activity activity, final @Nullable Bundle savedInstanceState) {
    setColdStart(savedInstanceState);
    addBreadcrumb(activity, "created");
    startTracing(activity);
    final @Nullable ISpan ttfdSpan = ttfdSpanMap.get(activity);

    firstActivityCreated = true;

    if (fullyDisplayedReporter != null) {
      fullyDisplayedReporter.registerFullyDrawnListener(() -> onFullFrameDrawn(ttfdSpan));
    }
  }

  @Override
  public synchronized void onActivityStarted(final @NotNull Activity activity) {
    // The docs on the screen rendering performance tracing
    // (https://firebase.google.com/docs/perf-mon/screen-traces?platform=android#definition),
    // state that the tracing starts for every Activity class when the app calls .onActivityStarted.
    // Adding an Activity in onActivityCreated leads to Window.FEATURE_NO_TITLE not
    // working. Moving this to onActivityStarted fixes the problem.
    activityFramesTracker.addActivity(activity);

    addBreadcrumb(activity, "started");
  }

  @SuppressLint("NewApi")
  @Override
  public synchronized void onActivityResumed(final @NotNull Activity activity) {

    // app start span
    @Nullable final SentryDate appStartStartTime = AppStartState.getInstance().getAppStartTime();
    @Nullable final SentryDate appStartEndTime = AppStartState.getInstance().getAppStartEndTime();
    // in case the SentryPerformanceProvider is disabled it does not set the app start times,
    // and we need to set the end time manually here,
    // the start time gets set manually in SentryAndroid.init()
    if (appStartStartTime != null && appStartEndTime == null) {
      AppStartState.getInstance().setAppStartEnd();
    }
    finishAppStartSpan();

    final @Nullable ISpan ttidSpan = ttidSpanMap.get(activity);
    final @Nullable ISpan ttfdSpan = ttfdSpanMap.get(activity);
    final View rootView = activity.findViewById(android.R.id.content);
    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.JELLY_BEAN
        && rootView != null) {
      FirstDrawDoneListener.registerForNextDraw(
          rootView, () -> onFirstFrameDrawn(ttfdSpan, ttidSpan), buildInfoProvider);
    } else {
      // Posting a task to the main thread's handler will make it executed after it finished
      // its current job. That is, right after the activity draws the layout.
      mainHandler.post(() -> onFirstFrameDrawn(ttfdSpan, ttidSpan));
    }
    addBreadcrumb(activity, "resumed");
  }

  @Override
  public void onActivityPostResumed(@NonNull Activity activity) {
    // empty override, required to avoid a api-level breaking super.onActivityPostResumed() calls
  }

  @Override
  public void onActivityPrePaused(@NonNull Activity activity) {
    // only executed if API >= 29 otherwise it happens on onActivityPaused
    if (isAllActivityCallbacksAvailable) {
      if (hub == null) {
        lastPausedTime = AndroidDateUtils.getCurrentSentryDateTime();
      } else {
        lastPausedTime = hub.getOptions().getDateProvider().now();
      }
    }
  }

  @Override
  public synchronized void onActivityPaused(final @NotNull Activity activity) {
    // only executed if API < 29 otherwise it happens on onActivityPrePaused
    if (!isAllActivityCallbacksAvailable) {
      if (hub == null) {
        lastPausedTime = AndroidDateUtils.getCurrentSentryDateTime();
      } else {
        lastPausedTime = hub.getOptions().getDateProvider().now();
      }
    }
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

    // in case the appStartSpan isn't completed yet, we finish it as cancelled to avoid
    // memory leak
    finishSpan(appStartSpan, SpanStatus.CANCELLED);

    // we finish the ttidSpan as cancelled in case it isn't completed yet
    final ISpan ttidSpan = ttidSpanMap.get(activity);
    final ISpan ttfdSpan = ttfdSpanMap.get(activity);
    finishSpan(ttidSpan, SpanStatus.DEADLINE_EXCEEDED);

    // we finish the ttfdSpan as deadline_exceeded in case it isn't completed yet
    finishExceededTtfdSpan(ttfdSpan, ttidSpan);
    cancelTtfdAutoClose();

    // in case people opt-out enableActivityLifecycleTracingAutoFinish and forgot to finish it,
    // we make sure to finish it when the activity gets destroyed.
    stopTracing(activity, true);

    // set it to null in case its been just finished as cancelled
    appStartSpan = null;
    ttidSpanMap.remove(activity);
    ttfdSpanMap.remove(activity);

    // clear it up, so we don't start again for the same activity if the activity is in the activity
    // stack still.
    // if the activity is opened again and not in memory, transactions will be created normally.
    if (performanceEnabled) {
      activitiesWithOngoingTransactions.remove(activity);
    }
  }

  private void finishSpan(final @Nullable ISpan span) {
    if (span != null && !span.isFinished()) {
      span.finish();
    }
  }

  private void finishSpan(final @Nullable ISpan span, final @NotNull SentryDate endTimestamp) {
    finishSpan(span, endTimestamp, null);
  }

  private void finishSpan(
      final @Nullable ISpan span,
      final @NotNull SentryDate endTimestamp,
      final @Nullable SpanStatus spanStatus) {
    if (span != null && !span.isFinished()) {
      final @NotNull SpanStatus status =
          spanStatus != null
              ? spanStatus
              : span.getStatus() != null ? span.getStatus() : SpanStatus.OK;
      span.finish(status, endTimestamp);
    }
  }

  private void finishSpan(final @Nullable ISpan span, final @NotNull SpanStatus status) {
    if (span != null && !span.isFinished()) {
      span.finish(status);
    }
  }

  private void cancelTtfdAutoClose() {
    if (ttfdAutoCloseFuture != null) {
      ttfdAutoCloseFuture.cancel(false);
      ttfdAutoCloseFuture = null;
    }
  }

  private void onFirstFrameDrawn(final @Nullable ISpan ttfdSpan, final @Nullable ISpan ttidSpan) {
    if (options != null && ttidSpan != null) {
      final SentryDate endDate = options.getDateProvider().now();
      final long durationNanos = endDate.diff(ttidSpan.getStartDate());
      final long durationMillis = TimeUnit.NANOSECONDS.toMillis(durationNanos);
      ttidSpan.setMeasurement(
          MeasurementValue.KEY_TIME_TO_INITIAL_DISPLAY, durationMillis, MILLISECOND);

      if (ttfdSpan != null && ttfdSpan.isFinished()) {
        ttfdSpan.updateEndDate(endDate);
        // If the ttfd span was finished before the first frame we adjust the measurement, too
        ttidSpan.setMeasurement(
            MeasurementValue.KEY_TIME_TO_FULL_DISPLAY, durationMillis, MILLISECOND);
      }
      finishSpan(ttidSpan, endDate);
    } else {
      finishSpan(ttidSpan);
    }
  }

  private void onFullFrameDrawn(final @Nullable ISpan ttfdSpan) {
    if (options != null && ttfdSpan != null) {
      final SentryDate endDate = options.getDateProvider().now();
      final long durationNanos = endDate.diff(ttfdSpan.getStartDate());
      final long durationMillis = TimeUnit.NANOSECONDS.toMillis(durationNanos);
      ttfdSpan.setMeasurement(
          MeasurementValue.KEY_TIME_TO_FULL_DISPLAY, durationMillis, MILLISECOND);
      finishSpan(ttfdSpan, endDate);
    } else {
      finishSpan(ttfdSpan);
    }
    cancelTtfdAutoClose();
  }

  private void finishExceededTtfdSpan(
      final @Nullable ISpan ttfdSpan, final @Nullable ISpan ttidSpan) {
    if (ttfdSpan == null || ttfdSpan.isFinished()) {
      return;
    }
    ttfdSpan.setDescription(getExceededTtfdDesc(ttfdSpan));
    // We set the end timestamp of the ttfd span to be equal to the ttid span.
    final @Nullable SentryDate ttidEndDate = ttidSpan != null ? ttidSpan.getFinishDate() : null;
    final @NotNull SentryDate ttfdEndDate =
        ttidEndDate != null ? ttidEndDate : ttfdSpan.getStartDate();
    finishSpan(ttfdSpan, ttfdEndDate, SpanStatus.DEADLINE_EXCEEDED);
  }

  @TestOnly
  @NotNull
  WeakHashMap<Activity, ITransaction> getActivitiesWithOngoingTransactions() {
    return activitiesWithOngoingTransactions;
  }

  @TestOnly
  @NotNull
  ActivityFramesTracker getActivityFramesTracker() {
    return activityFramesTracker;
  }

  @TestOnly
  @Nullable
  ISpan getAppStartSpan() {
    return appStartSpan;
  }

  @TestOnly
  @NotNull
  WeakHashMap<Activity, ISpan> getTtidSpanMap() {
    return ttidSpanMap;
  }

  @TestOnly
  @NotNull
  WeakHashMap<Activity, ISpan> getTtfdSpanMap() {
    return ttfdSpanMap;
  }

  private void setColdStart(final @Nullable Bundle savedInstanceState) {
    if (!firstActivityCreated) {
      // if Activity has savedInstanceState then its a warm start
      // https://developer.android.com/topic/performance/vitals/launch-time#warm
      AppStartState.getInstance().setColdStart(savedInstanceState == null);
    }
  }

  private @NotNull String getTtidDesc(final @NotNull String activityName) {
    return activityName + " initial display";
  }

  private @NotNull String getTtfdDesc(final @NotNull String activityName) {
    return activityName + " full display";
  }

  private @NotNull String getExceededTtfdDesc(final @NotNull ISpan ttfdSpan) {
    final @Nullable String ttfdCurrentDescription = ttfdSpan.getDescription();
    if (ttfdCurrentDescription != null && ttfdCurrentDescription.endsWith(" - Deadline Exceeded"))
      return ttfdCurrentDescription;
    return ttfdSpan.getDescription() + " - Deadline Exceeded";
  }

  private @NotNull String getAppStartDesc(final boolean coldStart) {
    if (coldStart) {
      return "Cold Start";
    } else {
      return "Warm Start";
    }
  }

  private @NotNull String getAppStartOp(final boolean coldStart) {
    if (coldStart) {
      return APP_START_COLD;
    } else {
      return APP_START_WARM;
    }
  }

  private void finishAppStartSpan() {
    final @Nullable SentryDate appStartEndTime = AppStartState.getInstance().getAppStartEndTime();
    if (performanceEnabled && appStartEndTime != null) {
      finishSpan(appStartSpan, appStartEndTime);
    }
  }
}
