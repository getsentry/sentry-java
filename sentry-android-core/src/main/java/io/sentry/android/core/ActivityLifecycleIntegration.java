package io.sentry.android.core;

import static io.sentry.MeasurementUnit.Duration.MILLISECOND;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import androidx.annotation.NonNull;
import io.sentry.DateUtils;
import io.sentry.FullyDisplayedReporter;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Instrumenter;
import io.sentry.Integration;
import io.sentry.NoOpTransaction;
import io.sentry.Scope;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryLongDate;
import io.sentry.SentryOptions;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.android.core.internal.util.FirstDrawDoneListener;
import io.sentry.android.core.performance.ActivityLifecycleTimeSpan;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.TimeSpan;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.Objects;
import io.sentry.util.TracingUtils;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

public final class ActivityLifecycleIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  public static class ScreenSpans {
    public final long startUptimeMs;
    public @Nullable ISpan ttid;
    public @Nullable ISpan ttfd;

    public ScreenSpans(final long startUptimeMs) {
      this.startUptimeMs = startUptimeMs;
    }
  }

  static final String UI_LOAD_OP = "ui.load";
  static final String APP_START_WARM = "app.start.warm";
  static final String APP_START_COLD = "app.start.cold";
  static final String TTID_OP = "ui.load.initial_display";
  static final String TTFD_OP = "ui.load.full_display";
  static final long TTFD_TIMEOUT_MILLIS = 30000;
  private static final String TRACE_ORIGIN = "auto.ui.activity";

  private final @NotNull Application application;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private @Nullable IHub hub;
  private @Nullable SentryAndroidOptions options;

  private boolean performanceEnabled = false;
  private boolean timeToFullDisplaySpanEnabled = false;
  private boolean isAllActivityCallbacksAvailable;

  private boolean firstActivityCreated = false;
  private final @NotNull AtomicBoolean firstDrawDone = new AtomicBoolean(false);

  private @Nullable FullyDisplayedReporter fullyDisplayedReporter = null;

  private final @NotNull WeakHashMap<Activity, ScreenSpans> activitySpanMap = new WeakHashMap<>();

  private SentryDate lastPausedTime = AndroidDateUtils.getCurrentSentryDateTime();
  private long lastPausedUptimeMs;

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
  }

  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.hub = Objects.requireNonNull(hub, "Hub is required");

    performanceEnabled = isPerformanceEnabled(this.options);
    fullyDisplayedReporter = this.options.getFullyDisplayedReporter();
    timeToFullDisplaySpanEnabled = this.options.isEnableTimeToFullDisplayTracing();

    application.registerActivityLifecycleCallbacks(this);
    this.options.getLogger().log(SentryLevel.DEBUG, "ActivityLifecycleIntegration installed.");
    addIntegrationToSdkVersion();
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
      finishTransaction(transaction, activitySpanMap.get(entry.getKey()));
    }
  }

  private void startTracing(final @NotNull Activity activity) {
    if (hub == null) {
      return;
    }
    if (options == null) {
      return;
    }

    if (isRunningTransactionOrTrace(activity)) {
      hub.getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Skipping trace start, because there's a running transaction/trace for this activity already.");
      return;
    }

    // TwP: just create a new trace, but not a new txn
    if (!performanceEnabled) {
      activitiesWithOngoingTransactions.put(activity, NoOpTransaction.getInstance());
      TracingUtils.startNewTrace(hub);
      return;
    }

    // as we allow a single transaction running on the bound Scope, we finish the previous ones
    stopPreviousTransactions();

    final WeakReference<Activity> weakActivity = new WeakReference<>(activity);
    final String activityName = getActivityName(activity);
    final AppStartMetrics appStartMetrics = AppStartMetrics.getInstance();
    final SentryDate appStartTime =
        new SentryLongDate(
            DateUtils.millisToNanos(appStartMetrics.getAppStartTimeSpan().getStartTimestampMs()));
    final AppStartMetrics.AppStartType appStartType = appStartMetrics.getAppStartType();

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
            activityFramesTracker.setMetrics(unwrappedActivity, finishingTransaction.getEventId());
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

    final long spanStartUptimeMs;
    if (firstActivityCreated) {
      // The ttid/ttfd spans should start when the previous activity called its onPause method
      spanStartUptimeMs = lastPausedUptimeMs;
      transactionOptions.setStartTimestamp(lastPausedTime);
    } else {
      // The first activity ttid / ttfd spans should start at the app start time
      transactionOptions.setStartTimestamp(appStartTime);
      spanStartUptimeMs = appStartMetrics.getAppStartTimeSpan().getStartUptimeMs();
    }

    // we can only bind to the scope if there's no running transaction
    final String transactionOp;
    if (appStartType == AppStartMetrics.AppStartType.COLD) {
      transactionOp = APP_START_COLD;
    } else if (appStartType == AppStartMetrics.AppStartType.WARM) {
      transactionOp = APP_START_WARM;
    } else {
      transactionOp = UI_LOAD_OP;
    }

    final @NotNull ITransaction transaction =
        hub.startTransaction(
            new TransactionContext(activityName, TransactionNameSource.COMPONENT, transactionOp),
            transactionOptions);
    setSpanOrigin(transaction);

    final ScreenSpans screenSpans = new ScreenSpans(spanStartUptimeMs);
    activitySpanMap.put(activity, screenSpans);

    final @NotNull ISpan ttidSpan =
        transaction.startChild(
            TTID_OP, getTtidDesc(activityName), transaction.getStartDate(), Instrumenter.SENTRY);
    setSpanOrigin(ttidSpan);
    screenSpans.ttid = ttidSpan;

    if (timeToFullDisplaySpanEnabled && fullyDisplayedReporter != null && options != null) {
      final @NotNull ISpan ttfdSpan =
          transaction.startChild(
              TTFD_OP, getTtfdDesc(activityName), transaction.getStartDate(), Instrumenter.SENTRY);
      setSpanOrigin(ttfdSpan);
      screenSpans.ttfd = ttfdSpan;

      try {
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

  private static void attachAppStartMetrics(
      final @NotNull AppStartMetrics appStartMetrics, final @NonNull ISpan parentSpan) {
    if (appStartMetrics.getAppStartType() == AppStartMetrics.AppStartType.UNKNOWN) {
      return;
    }

    // Application.onCreate
    final @NotNull TimeSpan appOnCreate = appStartMetrics.getApplicationOnCreateTimeSpan();
    if (appOnCreate.hasStopped()) {
      final @NotNull ISpan span =
          parentSpan.startChild(
              "ui.load",
              appOnCreate.getDescription(),
              new SentryLongDate(DateUtils.millisToNanos(appOnCreate.getStartTimestampMs())),
              Instrumenter.SENTRY);
      span.finish(
          SpanStatus.OK,
          new SentryLongDate(DateUtils.millisToNanos(appOnCreate.getProjectedStopTimestampMs())));
    }

    // Content Provider
    final @NotNull List<TimeSpan> contentProviderOnCreates =
        appStartMetrics.getContentProviderOnCreateTimeSpans();
    if (!contentProviderOnCreates.isEmpty()) {
      final @NotNull ISpan contentProviderRootSpan =
          parentSpan.startChild(
              "ui.load",
              "ContentProvider",
              new SentryLongDate(
                  DateUtils.millisToNanos(contentProviderOnCreates.get(0).getStartTimestampMs())),
              Instrumenter.SENTRY);
      for (TimeSpan contentProvider : contentProviderOnCreates) {
        final @NotNull ISpan contentProviderSpan =
            contentProviderRootSpan.startChild(
                "ui.load",
                contentProvider.getDescription(),
                new SentryLongDate(DateUtils.millisToNanos(contentProvider.getStartTimestampMs())),
                Instrumenter.SENTRY);
        contentProviderSpan.finish(
            SpanStatus.OK,
            new SentryLongDate(
                DateUtils.millisToNanos(contentProvider.getProjectedStopTimestampMs())));
      }
      contentProviderRootSpan.finish(
          SpanStatus.OK,
          new SentryLongDate(
              DateUtils.millisToNanos(
                  contentProviderOnCreates
                      .get(contentProviderOnCreates.size() - 1)
                      .getProjectedStopTimestampMs())));
    }

    // Activities
    final @NotNull List<ActivityLifecycleTimeSpan> activityLifecycleTimeSpans =
        appStartMetrics.getActivityLifecycleTimeSpans();
    if (!activityLifecycleTimeSpans.isEmpty()) {
      final @NotNull ISpan activityRootSpan =
          parentSpan.startChild(
              "ui.load",
              "Activity",
              new SentryLongDate(
                  DateUtils.millisToNanos(
                      activityLifecycleTimeSpans.get(0).onCreate.getStartTimestampMs())),
              Instrumenter.SENTRY);
      for (ActivityLifecycleTimeSpan activityTimeSpan : activityLifecycleTimeSpans) {
        final @NotNull ISpan onCreateSpan =
            activityRootSpan.startChild(
                "ui.load",
                activityTimeSpan.onCreate.getDescription(),
                new SentryLongDate(
                    DateUtils.millisToNanos(activityTimeSpan.onCreate.getStartTimestampMs())),
                Instrumenter.SENTRY);
        onCreateSpan.finish(
            SpanStatus.OK,
            new SentryLongDate(
                DateUtils.millisToNanos(activityTimeSpan.onCreate.getProjectedStopTimestampMs())));

        final @NotNull ISpan onStartSpan =
            activityRootSpan.startChild(
                "ui.load",
                activityTimeSpan.onStart.getDescription(),
                new SentryLongDate(
                    DateUtils.millisToNanos(activityTimeSpan.onStart.getStartTimestampMs())),
                Instrumenter.SENTRY);
        onStartSpan.finish(
            SpanStatus.OK,
            new SentryLongDate(
                DateUtils.millisToNanos(activityTimeSpan.onStart.getProjectedStopTimestampMs())));
      }
      activityRootSpan.finish(
          SpanStatus.OK,
          new SentryLongDate(
              DateUtils.millisToNanos(
                  activityLifecycleTimeSpans
                      .get(activityLifecycleTimeSpans.size() - 1)
                      .onStart
                      .getProjectedStopTimestampMs())));
    }
  }

  private void setSpanOrigin(ISpan span) {
    if (span != null) {
      span.getSpanContext().setOrigin(TRACE_ORIGIN);
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

  private boolean isRunningTransactionOrTrace(final @NotNull Activity activity) {
    return activitiesWithOngoingTransactions.containsKey(activity);
  }

  private void stopTracing(final @NotNull Activity activity, final boolean shouldFinishTracing) {
    if (performanceEnabled && shouldFinishTracing) {
      final ITransaction transaction = activitiesWithOngoingTransactions.get(activity);
      finishTransaction(transaction, activitySpanMap.get(activity));
    }
  }

  private void finishTransaction(
      final @Nullable ITransaction transaction, final @Nullable ScreenSpans spans) {

    @Nullable ISpan ttidSpan = spans != null ? spans.ttid : null;
    @Nullable ISpan ttfdSpan = spans != null ? spans.ttfd : null;

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
    startTracing(activity);
    firstActivityCreated = true;

    if (fullyDisplayedReporter != null) {
      final @Nullable ScreenSpans spans = activitySpanMap.get(activity);
      final @Nullable ISpan ttfdSpan = spans != null ? spans.ttfd : null;
      fullyDisplayedReporter.registerFullyDrawnListener(() -> onFullFrameDrawn(ttfdSpan));
    }
  }

  @Override
  public synchronized void onActivityStarted(final @NotNull Activity activity) {
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

  @Override
  public void onActivityStopped(@NonNull Activity activity) {
    // no-op
  }

  @Override
  public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    // no-op
  }

  @SuppressLint("NewApi")
  @Override
  public synchronized void onActivityResumed(final @NotNull Activity activity) {
    if (performanceEnabled) {
      final View rootView = activity.findViewById(android.R.id.content);
      if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.JELLY_BEAN
          && rootView != null) {
        FirstDrawDoneListener.registerForNextDraw(
            rootView, () -> onFirstFrameDrawn(activity), buildInfoProvider);
      } else {
        // Posting a task to the main thread's handler will make it executed after it finished
        // its current job. That is, right after the activity draws the layout.
        mainHandler.post(() -> onFirstFrameDrawn(activity));
      }
    }
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
      lastPausedUptimeMs = SystemClock.uptimeMillis();
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
      lastPausedUptimeMs = SystemClock.uptimeMillis();
    }
  }

  @Override
  public synchronized void onActivityDestroyed(final @NotNull Activity activity) {
    if (performanceEnabled) {

      // we finish the ttidSpan as cancelled in case it isn't completed yet
      final @Nullable ScreenSpans spans = activitySpanMap.get(activity);

      final ISpan ttidSpan = spans != null ? spans.ttid : null;
      final ISpan ttfdSpan = spans != null ? spans.ttfd : null;
      finishSpan(ttidSpan, SpanStatus.DEADLINE_EXCEEDED);

      // we finish the ttfdSpan as deadline_exceeded in case it isn't completed yet
      finishExceededTtfdSpan(ttfdSpan, ttidSpan);
      cancelTtfdAutoClose();

      // in case people opt-out enableActivityLifecycleTracingAutoFinish and forgot to finish it,
      // we make sure to finish it when the activity gets destroyed.
      stopTracing(activity, true);

      // set it to null in case its been just finished as cancelled
      activitySpanMap.remove(activity);
    }

    // clear it up, so we don't start again for the same activity if the activity is in the
    // activity
    // stack still.
    // if the activity is opened again and not in memory, transactions will be created normally.
    activitiesWithOngoingTransactions.remove(activity);
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

  private void onFirstFrameDrawn(final @NotNull Activity activity) {
    final long nowUptimeMs = SystemClock.uptimeMillis();

    if (options == null) {
      return;
    }

    final @Nullable ScreenSpans spans = activitySpanMap.get(activity);
    if (spans == null) {
      return;
    }

    final boolean wasFirstDraw = firstDrawDone.compareAndSet(false, true);

    final @Nullable ISpan ttidSpan = spans.ttid;
    final @Nullable ISpan ttfdSpan = spans.ttfd;

    if (ttidSpan == null) {
      return;
    }

    long durationMillis = 0;
    // attach the app start metrics only once, to the first drawn activity
    if (wasFirstDraw) {
      final @NotNull AppStartMetrics appStartMetrics = AppStartMetrics.getInstance();
      attachAppStartMetrics(appStartMetrics, ttidSpan);
      appStartMetrics.clear();

      if (appStartMetrics.getAppStartTimeSpan().hasStopped()) {
        durationMillis = appStartMetrics.getAppStartTimeSpan().getDurationMs();
      }
    }

    if (durationMillis == 0) {
      durationMillis = nowUptimeMs - spans.startUptimeMs;
    }
    // project the end date, to keep being monotonic
    final SentryDate endDate =
        new SentryLongDate(
            ttidSpan.getStartDate().nanoTimestamp() + DateUtils.millisToNanos(durationMillis));
    ttidSpan.setMeasurement(
        MeasurementValue.KEY_TIME_TO_INITIAL_DISPLAY, durationMillis, MILLISECOND);

    // If the ttfd span was finished before the first frame we adjust the measurement, too
    if (ttfdSpan != null && ttfdSpan.isFinished()) {
      ttfdSpan.updateEndDate(endDate);
      ttidSpan.setMeasurement(
          MeasurementValue.KEY_TIME_TO_FULL_DISPLAY, durationMillis, MILLISECOND);
    }
    finishSpan(ttidSpan, endDate);
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
  @NotNull
  WeakHashMap<Activity, ScreenSpans> getScreenSpans() {
    return activitySpanMap;
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

  private @NotNull String getAppStartDesc(final AppStartMetrics.AppStartType appStartType) {
    if (appStartType == AppStartMetrics.AppStartType.COLD) {
      return "Cold Start";
    } else {
      return "Warm Start";
    }
  }

  private @NotNull String getAppStartOp(final AppStartMetrics.AppStartType appStartType) {
    if (appStartType == AppStartMetrics.AppStartType.COLD) {
      return APP_START_COLD;
    } else {
      return APP_START_WARM;
    }
  }
}
