package io.sentry.android.core;

import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.SentryDate;
import io.sentry.SpanContext;
import io.sentry.SpanStatus;
import io.sentry.TracesSamplingDecision;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.TimeSpan;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns standalone app-start transactions: activity-launch siblings, headless emit-and-finish, trace
 * reuse for a later {@code ui.load}, and transaction classification for the event processor.
 *
 * <p>{@link AppStartMetrics} remains a pre-init historian and schedules the main-idle/no-activity
 * check. This reporter subscribes via {@link AppStartMetrics.OnMainIdleNoActivityCallback} and
 * turns that signal into a transaction once {@link IScopes} are available.
 */
final class StandaloneAppStartReporter
    implements StandaloneAppStartCoordinator, AppStartMetrics.OnMainIdleNoActivityCallback {

  static final String STANDALONE_APP_START_OP = "app.start";
  static final String STANDALONE_APP_START_NAME = "App Start";
  static final String APP_START_SCREEN_DATA = "app.vitals.start.screen";

  private final @NotNull IScopes scopes;
  private final @NotNull String traceOrigin;

  /** The activity-launch sibling transaction. Headless starts are emitted and finished inline. */
  private @Nullable ITransaction appStartTransaction;

  /** Trace id from a headless app start, to be reused by a later activity so both share a trace. */
  private volatile @Nullable SentryId reusableTraceId;

  StandaloneAppStartReporter(final @NotNull IScopes scopes, final @NotNull String traceOrigin) {
    this.scopes = scopes;
    this.traceOrigin = traceOrigin;
  }

  void register() {
    AppStartMetrics.getInstance().setOnMainIdleNoActivityCallback(this);
  }

  void close() {
    AppStartMetrics.getInstance().setOnMainIdleNoActivityCallback(null);
  }

  @Override
  public @NotNull ITransaction startForActivity(
      final @NotNull SentryId traceId,
      final @NotNull String activityName,
      final @NotNull SentryDate appStartTime,
      final @Nullable TracesSamplingDecision samplingDecision) {
    final TransactionOptions txnOptions = new TransactionOptions();
    txnOptions.setBindToScope(false);
    txnOptions.setStartTimestamp(appStartTime);
    txnOptions.setAppStartTransaction(samplingDecision != null);
    txnOptions.setOrigin(traceOrigin);

    final ITransaction transaction =
        scopes.startTransaction(
            new TransactionContext(
                traceId,
                STANDALONE_APP_START_NAME,
                TransactionNameSource.COMPONENT,
                STANDALONE_APP_START_OP,
                samplingDecision),
            txnOptions);
    transaction.setData(APP_START_SCREEN_DATA, activityName);
    appStartTransaction = transaction;
    return transaction;
  }

  @Override
  public @Nullable ISpan getAppStartTransaction() {
    return appStartTransaction;
  }

  @Override
  public void finishAppStart(final @NotNull SentryDate endDate) {
    if (appStartTransaction != null && !appStartTransaction.isFinished()) {
      appStartTransaction.finish(SpanStatus.OK, endDate);
    }
  }

  @Override
  public void onActivityDestroyed() {
    if (appStartTransaction != null && !appStartTransaction.isFinished()) {
      appStartTransaction.finish(SpanStatus.CANCELLED);
    }
    appStartTransaction = null;
  }

  @Override
  public @Nullable SentryId getReusableTraceId() {
    return reusableTraceId;
  }

  @Override
  public void setReusableTraceId(final @Nullable SentryId traceId) {
    this.reusableTraceId = traceId;
  }

  @Override
  public void onMainIdleNoActivity() {
    final @NotNull AppStartMetrics metrics = AppStartMetrics.getInstance();
    // Profilers are stopped in AppStartMetrics; clear the decision so it doesn't leak to a later
    // ui.load transaction if an activity eventually opens.
    metrics.setAppStartSamplingDecision(null);
    metrics.finalizeHeadlessAppStartEndTime();
    emitHeadlessAppStartTransaction(metrics);
  }

  private void emitHeadlessAppStartTransaction(final @NotNull AppStartMetrics metrics) {
    final @NotNull TimeSpan appStartTimeSpan = metrics.getAppStartTimeSpanForHeadless();
    if (!appStartTimeSpan.hasStarted() || !appStartTimeSpan.hasStopped()) {
      return;
    }

    final @Nullable SentryDate startTime = appStartTimeSpan.getStartTimestamp();
    final @Nullable SentryDate endTime = appStartTimeSpan.getProjectedStopTimestamp();
    if (startTime == null || endTime == null) {
      return;
    }

    final TransactionOptions txnOptions = new TransactionOptions();
    txnOptions.setBindToScope(false);
    txnOptions.setStartTimestamp(startTime);
    txnOptions.setOrigin(traceOrigin);

    final ITransaction transaction =
        scopes.startTransaction(
            new TransactionContext(
                STANDALONE_APP_START_NAME,
                TransactionNameSource.COMPONENT,
                STANDALONE_APP_START_OP,
                null),
            txnOptions);
    reusableTraceId = transaction.getSpanContext().getTraceId();
    transaction.finish(SpanStatus.OK, endTime);
  }

  /** Whether the transaction is a standalone {@code App Start} transaction this reporter emits. */
  static boolean isStandaloneAppStart(final @Nullable SpanContext context) {
    return context != null && STANDALONE_APP_START_OP.equals(context.getOperation());
  }

  /**
   * Whether the transaction is a headless (non-activity) standalone app start. Headless starts are
   * the only standalone app starts without the {@link #APP_START_SCREEN_DATA} screen marker, which
   * this reporter sets exclusively on the activity-launch path.
   */
  static boolean isHeadlessAppStart(final @Nullable SpanContext context) {
    return isStandaloneAppStart(context) && !context.getData().containsKey(APP_START_SCREEN_DATA);
  }
}
