package io.sentry.android.core;

import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.SentryDate;
import io.sentry.TracesSamplingDecision;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Callbacks from {@link ActivityLifecycleIntegration} for standalone app-start tracing. Registered
 * by {@link AppStartIntegration} during SDK init.
 */
@ApiStatus.Internal
interface FirstUiLoadListener {

  /**
   * Builds the {@code ui.load} {@link TransactionContext}. On the first activity of a process
   * start, consumes any trace id stashed by a prior headless app start.
   */
  @NotNull
  UiLoadStartPlan planFirstUiLoad(
      @NotNull String activityName,
      @Nullable TracesSamplingDecision samplingDecision,
      boolean isFirstProcessStart);

  /**
   * The first {@code ui.load} transaction was started. Emits a sibling {@code App Start}
   * transaction when {@link UiLoadStartPlan#shouldEmitSiblingAppStart()} is true.
   */
  void onFirstUiLoadTransactionStarted(
      @NotNull ITransaction uiLoadTransaction,
      @NotNull UiLoadStartPlan plan,
      @NotNull SentryDate appStartTime,
      @NotNull String activityName,
      @Nullable TracesSamplingDecision samplingDecision);

  /** Parent for {@code activity.load} spans during the first activity, if applicable. */
  @Nullable
  ISpan getAppStartTransaction();

  /** Finish the activity-launch app-start transaction at first frame. */
  void onFirstFrameDrawn(@NotNull SentryDate endDate);

  /** Cancel and clear the activity-launch app-start transaction. */
  void onActivityDestroyed();
}
