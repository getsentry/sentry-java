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
 * Owns the standalone app-start feature end to end: emitting the {@code App Start} transaction
 * (both the activity-launch path and the headless, non-activity path), keeping its trace linked to
 * the following {@code ui.load} transaction, and classifying transactions for the event processor.
 *
 * <p>This lives on the post-init side of the SDK so it can hold an {@link IScopes} and start
 * transactions. {@link AppStartMetrics} remains a pre-init, scope-less recorder; it emits the
 * "headless app start happened" signal via {@link AppStartMetrics.HeadlessAppStartListener} and
 * this reporter turns that signal into a transaction. Only data crosses the pre-/post-init seam,
 * not logic.
 */
final class StandaloneAppStartReporter implements AppStartMetrics.HeadlessAppStartListener {

  static final String STANDALONE_APP_START_OP = "app.start";
  static final String STANDALONE_APP_START_NAME = "App Start";
  static final String APP_START_SCREEN_DATA = "app.vitals.start.screen";

  private final @NotNull IScopes scopes;
  private final @NotNull String traceOrigin;

  /** The activity-launch sibling transaction. Headless starts are emitted and finished inline. */
  private @Nullable ITransaction appStartTransaction;

  /**
   * Trace id from a headless app start, to be reused by a later activity so both share a trace.
   * Owned here rather than parked on the {@link AppStartMetrics} singleton.
   */
  private volatile @Nullable SentryId reusableTraceId;

  StandaloneAppStartReporter(final @NotNull IScopes scopes, final @NotNull String traceOrigin) {
    this.scopes = scopes;
    this.traceOrigin = traceOrigin;
  }

  void register() {
    AppStartMetrics.getInstance().setHeadlessAppStartListener(this);
  }

  void close() {
    AppStartMetrics.getInstance().setHeadlessAppStartListener(null);
  }

  /**
   * Starts the activity-launch {@code App Start} transaction as a sibling of the {@code ui.load}
   * transaction, sharing its trace id. The caller is responsible for finishing it via {@link
   * #finishAppStart(SentryDate)}.
   */
  @NotNull
  ITransaction startForActivity(
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

  /** The current activity-launch app-start transaction, used as a parent for lifecycle spans. */
  @Nullable
  ISpan getAppStartTransaction() {
    return appStartTransaction;
  }

  /** Finishes the activity-launch app-start transaction at {@code endDate}, if one is running. */
  void finishAppStart(final @NotNull SentryDate endDate) {
    if (appStartTransaction != null && !appStartTransaction.isFinished()) {
      appStartTransaction.finish(SpanStatus.OK, endDate);
    }
  }

  /** Cancels and clears the activity-launch app-start transaction to avoid leaking it. */
  void onActivityDestroyed() {
    if (appStartTransaction != null && !appStartTransaction.isFinished()) {
      appStartTransaction.finish(SpanStatus.CANCELLED);
    }
    appStartTransaction = null;
  }

  /** Returns the stored trace id, if a prior headless app start emitted one. */
  @Nullable
  SentryId getReusableTraceId() {
    return reusableTraceId;
  }

  void setReusableTraceId(final @Nullable SentryId traceId) {
    this.reusableTraceId = traceId;
  }

  @Override
  public void onHeadlessAppStart() {
    final @NotNull AppStartMetrics metrics = AppStartMetrics.getInstance();
    // Profilers are stopped for headless starts; clear the decision so it doesn't
    // leak to a later ui.load transaction if an activity eventually opens.
    metrics.setAppStartSamplingDecision(null);

    // For headless starts, appLaunchedInForeground is false, so we can't use
    // getAppStartTimeSpanWithFallback (which gates on foreground).
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
