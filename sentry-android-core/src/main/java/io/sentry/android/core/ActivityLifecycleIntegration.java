package io.sentry.android.core;

import static io.sentry.MeasurementUnit.Duration.MILLISECOND;
import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import io.sentry.FullyDisplayedReporter;
import io.sentry.IScope;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Instrumenter;
import io.sentry.Integration;
import io.sentry.NoOpTransaction;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryNanotimeDate;
import io.sentry.SentryOptions;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.TracesSamplingDecision;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.android.core.internal.util.ClassUtil;
import io.sentry.android.core.internal.util.FirstDrawDoneListener;
import io.sentry.android.core.performance.ActivityLifecycleSpanHelper;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.TimeSpan;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import io.sentry.util.TracingUtils;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Date;
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
  static final long TTFD_TIMEOUT_MILLIS = 25000;
  private static final String TRACE_ORIGIN = "auto.ui.activity";

  private final @NotNull Application application;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private @Nullable IScopes scopes;
  private @Nullable SentryAndroidOptions options;

  private boolean performanceEnabled = false;

  private boolean timeToFullDisplaySpanEnabled = false;

  private boolean isAllActivityCallbacksAvailable;

  private boolean firstActivityCreated = false;

  private @Nullable FullyDisplayedReporter fullyDisplayedReporter = null;
  private @Nullable ISpan appStartSpan;
  private final @NotNull WeakHashMap<Activity, ISpan> ttidSpanMap = new WeakHashMap<>();
  private final @NotNull WeakHashMap<Activity, ISpan> ttfdSpanMap = new WeakHashMap<>();
  private final @NotNull WeakHashMap<Activity, ActivityLifecycleSpanHelper> activitySpanHelpers =
      new WeakHashMap<>();
  private @NotNull SentryDate lastPausedTime = new SentryNanotimeDate(new Date(0), 0);
  private @Nullable Future<?> ttfdAutoCloseFuture = null;

  // WeakHashMap isn't thread safe but ActivityLifecycleCallbacks is only called from the
  // main-thread
  private final @NotNull WeakHashMap<Activity, ITransaction> activitiesWithOngoingTransactions =
      new WeakHashMap<>();

  private final @NotNull ActivityFramesTracker activityFramesTracker;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();
  private boolean fullyDisplayedCalled = false;
  private final @NotNull AutoClosableReentrantLock fullyDisplayedLock =
      new AutoClosableReentrantLock();

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
  }

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");

    performanceEnabled = isPerformanceEnabled(this.options);
    fullyDisplayedReporter = this.options.getFullyDisplayedReporter();
    timeToFullDisplaySpanEnabled = this.options.isEnableTimeToFullDisplayTracing();

    application.registerActivityLifecycleCallbacks(this);
    this.options.getLogger().log(SentryLevel.DEBUG, "ActivityLifecycleIntegration installed.");
    addIntegrationToSdkVersion("ActivityLifecycle");
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
    if (scopes != null && !isRunningTransactionOrTrace(activity)) {
      if (!performanceEnabled) {
        activitiesWithOngoingTransactions.put(activity, NoOpTransaction.getInstance());
        if (options.isEnableAutoTraceIdGeneration()) {
          TracingUtils.startNewTrace(scopes);
        }
      } else {
        // as we allow a single transaction running on the bound Scope, we finish the previous ones
        stopPreviousTransactions();

        final String activityName = getActivityName(activity);

        final @Nullable SentryDate appStartTime;
        final @Nullable Boolean coldStart;
        final @NotNull TimeSpan appStartTimeSpan =
            AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options);

        // we only track app start for processes that will show an Activity (full launch).
        // Here we check the process importance which will tell us that.
        final boolean foregroundImportance = ContextUtils.isForegroundImportance();
        if (foregroundImportance && appStartTimeSpan.hasStarted()) {
          appStartTime = appStartTimeSpan.getStartTimestamp();
          coldStart =
              AppStartMetrics.getInstance().getAppStartType() == AppStartMetrics.AppStartType.COLD;
        } else {
          appStartTime = null;
          coldStart = null;
        }

        final TransactionOptions transactionOptions = new TransactionOptions();

        // Set deadline timeout based on configured option
        final long deadlineTimeoutMillis = options.getAutoTransactionDeadlineTimeoutMillis();
        if (deadlineTimeoutMillis <= 0) {
          // No deadline when zero or negative value is set
          transactionOptions.setDeadlineTimeout(null);
        } else {
          // Use configured timeout when positive value is set
          transactionOptions.setDeadlineTimeout(deadlineTimeoutMillis);
        }

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
        final @Nullable TracesSamplingDecision appStartSamplingDecision;

        if (!(firstActivityCreated || appStartTime == null || coldStart == null)) {
          // The first activity ttid/ttfd spans should start at the app start time
          ttidStartTime = appStartTime;
          // The app start transaction inherits the sampling decision from the app start profiling,
          // then clears it
          appStartSamplingDecision = AppStartMetrics.getInstance().getAppStartSamplingDecision();
          AppStartMetrics.getInstance().setAppStartSamplingDecision(null);
        } else {
          // The ttid/ttfd spans should start when the previous activity called its onPause method
          ttidStartTime = lastPausedTime;
          appStartSamplingDecision = null;
        }
        transactionOptions.setStartTimestamp(ttidStartTime);
        transactionOptions.setAppStartTransaction(appStartSamplingDecision != null);
        setSpanOrigin(transactionOptions);

        // we can only bind to the scope if there's no running transaction
        ITransaction transaction =
            scopes.startTransaction(
                new TransactionContext(
                    activityName,
                    TransactionNameSource.COMPONENT,
                    UI_LOAD_OP,
                    appStartSamplingDecision),
                transactionOptions);

        final SpanOptions spanOptions = new SpanOptions();
        setSpanOrigin(spanOptions);

        // in case appStartTime isn't available, we don't create a span for it.
        if (!(firstActivityCreated || appStartTime == null || coldStart == null)) {
          // start specific span for app start
          appStartSpan =
              transaction.startChild(
                  getAppStartOp(coldStart),
                  getAppStartDesc(coldStart),
                  appStartTime,
                  Instrumenter.SENTRY,
                  spanOptions);

          // in case there's already an end time (e.g. due to deferred SDK init)
          // we can finish the app-start span
          finishAppStartSpan();
        }
        final @NotNull ISpan ttidSpan =
            transaction.startChild(
                TTID_OP,
                getTtidDesc(activityName),
                ttidStartTime,
                Instrumenter.SENTRY,
                spanOptions);
        ttidSpanMap.put(activity, ttidSpan);

        if (timeToFullDisplaySpanEnabled && fullyDisplayedReporter != null && options != null) {
          final @NotNull ISpan ttfdSpan =
              transaction.startChild(
                  TTFD_OP,
                  getTtfdDesc(activityName),
                  ttidStartTime,
                  Instrumenter.SENTRY,
                  spanOptions);
          try {
            ttfdSpanMap.put(activity, ttfdSpan);
            ttfdAutoCloseFuture =
                options
                    .getExecutorService()
                    .schedule(
                        () -> finishExceededTtfdSpan(ttfdSpan, ttidSpan), TTFD_TIMEOUT_MILLIS);
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
        scopes.configureScope(
            scope -> {
              applyScope(scope, transaction);
            });

        activitiesWithOngoingTransactions.put(activity, transaction);
      }
    }
  }

  private void setSpanOrigin(final @NotNull SpanOptions spanOptions) {
    spanOptions.setOrigin(TRACE_ORIGIN);
  }

  @VisibleForTesting
  void applyScope(final @NotNull IScope scope, final @NotNull ITransaction transaction) {
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
  void clearScope(final @NotNull IScope scope, final @NotNull ITransaction transaction) {
    scope.withTransaction(
        scopeTransaction -> {
          if (scopeTransaction == transaction) {
            scope.clearTransaction();
          }
        });
  }

  private boolean isRunningTransactionOrTrace(final @NotNull Activity activity) {
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
      if (scopes != null) {
        // make sure to remove the transaction from scope, as it may contain running children,
        // therefore `finish` method will not remove it from scope
        scopes.configureScope(
            scope -> {
              clearScope(scope, transaction);
            });
      }
    }
  }

  @Override
  public void onActivityPreCreated(
      final @NotNull Activity activity, final @Nullable Bundle savedInstanceState) {
    final ActivityLifecycleSpanHelper helper =
        new ActivityLifecycleSpanHelper(activity.getClass().getName());
    activitySpanHelpers.put(activity, helper);
    // The very first activity start timestamp cannot be set to the class instantiation time, as it
    // may happen before an activity is started (service, broadcast receiver, etc). So we set it
    // here.
    if (firstActivityCreated) {
      return;
    }
    lastPausedTime =
        scopes != null
            ? scopes.getOptions().getDateProvider().now()
            : AndroidDateUtils.getCurrentSentryDateTime();
    helper.setOnCreateStartTimestamp(lastPausedTime);
  }

  @Override
  public void onActivityCreated(
      final @NotNull Activity activity, final @Nullable Bundle savedInstanceState) {
    if (!isAllActivityCallbacksAvailable) {
      onActivityPreCreated(activity, savedInstanceState);
    }
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (scopes != null && options != null && options.isEnableScreenTracking()) {
        final @Nullable String activityClassName = ClassUtil.getClassName(activity);
        scopes.configureScope(scope -> scope.setScreen(activityClassName));
      }
      startTracing(activity);
      final @Nullable ISpan ttidSpan = ttidSpanMap.get(activity);
      final @Nullable ISpan ttfdSpan = ttfdSpanMap.get(activity);

      firstActivityCreated = true;

      if (performanceEnabled
          && ttidSpan != null
          && ttfdSpan != null
          && fullyDisplayedReporter != null) {
        fullyDisplayedReporter.registerFullyDrawnListener(
            () -> onFullFrameDrawn(ttidSpan, ttfdSpan));
      }
    }
  }

  @Override
  public void onActivityPostCreated(
      final @NotNull Activity activity, final @Nullable Bundle savedInstanceState) {
    final ActivityLifecycleSpanHelper helper = activitySpanHelpers.get(activity);
    if (helper != null) {
      helper.createAndStopOnCreateSpan(
          appStartSpan != null ? appStartSpan : activitiesWithOngoingTransactions.get(activity));
    }
  }

  @Override
  public void onActivityPreStarted(final @NotNull Activity activity) {
    final ActivityLifecycleSpanHelper helper = activitySpanHelpers.get(activity);
    if (helper != null) {
      helper.setOnStartStartTimestamp(
          options != null
              ? options.getDateProvider().now()
              : AndroidDateUtils.getCurrentSentryDateTime());
    }
  }

  @Override
  public void onActivityStarted(final @NotNull Activity activity) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (!isAllActivityCallbacksAvailable) {
        onActivityPostCreated(activity, null);
        onActivityPreStarted(activity);
      }
      if (performanceEnabled) {
        // The docs on the screen rendering performance tracing
        // (https://firebase.google.com/docs/perf-mon/screen-traces?platform=android#definition),
        // state that the tracing starts for every Activity class when the app calls
        // .onActivityStarted.
        // Adding an Activity in onActivityCreated leads to Window.FEATURE_NO_TITLE not
        // working. Moving this to onActivityStarted fixes the problem.
        activityFramesTracker.addActivity(activity);
      }
    }
  }

  @Override
  public void onActivityPostStarted(final @NotNull Activity activity) {
    final ActivityLifecycleSpanHelper helper = activitySpanHelpers.get(activity);
    if (helper != null) {
      helper.createAndStopOnStartSpan(
          appStartSpan != null ? appStartSpan : activitiesWithOngoingTransactions.get(activity));
      // Needed to handle hybrid SDKs
      helper.saveSpanToAppStartMetrics();
    }
  }

  @Override
  public void onActivityResumed(final @NotNull Activity activity) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (!isAllActivityCallbacksAvailable) {
        onActivityPostStarted(activity);
      }
      if (performanceEnabled) {

        final @Nullable ISpan ttidSpan = ttidSpanMap.get(activity);
        final @Nullable ISpan ttfdSpan = ttfdSpanMap.get(activity);
        if (activity.getWindow() != null) {
          FirstDrawDoneListener.registerForNextDraw(
              activity, () -> onFirstFrameDrawn(ttfdSpan, ttidSpan), buildInfoProvider);
        } else {
          // Posting a task to the main thread's handler will make it executed after it finished
          // its current job. That is, right after the activity draws the layout.
          new Handler(Looper.getMainLooper()).post(() -> onFirstFrameDrawn(ttfdSpan, ttidSpan));
        }
      }
    }
  }

  @Override
  public void onActivityPostResumed(@NotNull Activity activity) {
    // empty override, required to avoid a api-level breaking super.onActivityPostResumed() calls
  }

  @Override
  public void onActivityPrePaused(@NotNull Activity activity) {
    // only executed if API >= 29 otherwise it happens on onActivityPaused
    // as the SDK may gets (re-)initialized mid activity lifecycle, ensure we set the flag here as
    // well
    // this ensures any newly launched activity will not use the app start timestamp as txn start
    firstActivityCreated = true;
    lastPausedTime =
        scopes != null
            ? scopes.getOptions().getDateProvider().now()
            : AndroidDateUtils.getCurrentSentryDateTime();
  }

  @Override
  public void onActivityPaused(final @NotNull Activity activity) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      // only executed if API < 29 otherwise it happens on onActivityPrePaused
      if (!isAllActivityCallbacksAvailable) {
        onActivityPrePaused(activity);
      }
    }
  }

  @Override
  public void onActivityStopped(final @NotNull Activity activity) {
    // no-op (acquire lock if this no longer is no-op)
  }

  @Override
  public void onActivitySaveInstanceState(
      final @NotNull Activity activity, final @NotNull Bundle outState) {
    // no-op (acquire lock if this no longer is no-op)
  }

  @Override
  public void onActivityDestroyed(final @NotNull Activity activity) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final ActivityLifecycleSpanHelper helper = activitySpanHelpers.remove(activity);
      if (helper != null) {
        helper.clear();
      }
      if (performanceEnabled) {

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
      }

      // clear it up, so we don't start again for the same activity if the activity is in the
      // activity stack still.
      // if the activity is opened again and not in memory, transactions will be created normally.
      activitiesWithOngoingTransactions.remove(activity);

      if (activitiesWithOngoingTransactions.isEmpty() && !activity.isChangingConfigurations()) {
        clear();
      }
    }
  }

  private void clear() {
    firstActivityCreated = false;
    lastPausedTime = new SentryNanotimeDate(new Date(0), 0);
    activitySpanHelpers.clear();
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
    // app start span
    final @NotNull AppStartMetrics appStartMetrics = AppStartMetrics.getInstance();
    final @NotNull TimeSpan appStartTimeSpan = appStartMetrics.getAppStartTimeSpan();
    final @NotNull TimeSpan sdkInitTimeSpan = appStartMetrics.getSdkInitTimeSpan();

    // and we need to set the end time of the app start here, after the first frame is drawn.
    if (appStartTimeSpan.hasStarted() && appStartTimeSpan.hasNotStopped()) {
      appStartTimeSpan.stop();
    }
    if (sdkInitTimeSpan.hasStarted() && sdkInitTimeSpan.hasNotStopped()) {
      sdkInitTimeSpan.stop();
    }
    finishAppStartSpan();

    // Sentry.reportFullyDisplayed can be run in any thread, so we have to ensure synchronization
    // with first frame drawn
    try (final @NotNull ISentryLifecycleToken ignored = fullyDisplayedLock.acquire()) {
      if (options != null && ttidSpan != null) {
        final SentryDate endDate = options.getDateProvider().now();
        final long durationNanos = endDate.diff(ttidSpan.getStartDate());
        final long durationMillis = TimeUnit.NANOSECONDS.toMillis(durationNanos);
        ttidSpan.setMeasurement(
            MeasurementValue.KEY_TIME_TO_INITIAL_DISPLAY, durationMillis, MILLISECOND);
        // If Sentry.reportFullyDisplayed was called before the first frame is drawn, we finish
        //  the ttfd now
        if (ttfdSpan != null && fullyDisplayedCalled) {
          fullyDisplayedCalled = false;
          ttidSpan.setMeasurement(
              MeasurementValue.KEY_TIME_TO_FULL_DISPLAY, durationMillis, MILLISECOND);
          ttfdSpan.setMeasurement(
              MeasurementValue.KEY_TIME_TO_FULL_DISPLAY, durationMillis, MILLISECOND);
          finishSpan(ttfdSpan, endDate);
        }

        finishSpan(ttidSpan, endDate);
      } else {
        finishSpan(ttidSpan);
        if (fullyDisplayedCalled) {
          finishSpan(ttfdSpan);
        }
      }
    }
  }

  private void onFullFrameDrawn(final @NotNull ISpan ttidSpan, final @NotNull ISpan ttfdSpan) {
    cancelTtfdAutoClose();
    // Sentry.reportFullyDisplayed can be run in any thread, so we have to ensure synchronization
    // with first frame drawn
    try (final @NotNull ISentryLifecycleToken ignored = fullyDisplayedLock.acquire()) {
      // If the TTID span didn't finish, it means the first frame was not drawn yet, which means
      // Sentry.reportFullyDisplayed was called too early. We set a flag, so that whenever the TTID
      // will finish, we will finish the TTFD span as well.
      if (!ttidSpan.isFinished()) {
        fullyDisplayedCalled = true;
        return;
      }
      if (options != null) {
        final SentryDate endDate = options.getDateProvider().now();
        final long durationNanos = endDate.diff(ttfdSpan.getStartDate());
        final long durationMillis = TimeUnit.NANOSECONDS.toMillis(durationNanos);
        ttfdSpan.setMeasurement(
            MeasurementValue.KEY_TIME_TO_FULL_DISPLAY, durationMillis, MILLISECOND);
        finishSpan(ttfdSpan, endDate);
      } else {
        finishSpan(ttfdSpan);
      }
    }
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
  WeakHashMap<Activity, ActivityLifecycleSpanHelper> getActivitySpanHelpers() {
    return activitySpanHelpers;
  }

  @TestOnly
  void setFirstActivityCreated(boolean firstActivityCreated) {
    this.firstActivityCreated = firstActivityCreated;
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
    final @Nullable SentryDate appStartEndTime =
        AppStartMetrics.getInstance()
            .getAppStartTimeSpanWithFallback(options)
            .getProjectedStopTimestamp();
    if (performanceEnabled && appStartEndTime != null) {
      finishSpan(appStartSpan, appStartEndTime);
    }
  }
}
