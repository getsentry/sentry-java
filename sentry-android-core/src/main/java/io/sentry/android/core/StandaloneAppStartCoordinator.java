package io.sentry.android.core;

import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.SentryDate;
import io.sentry.TracesSamplingDecision;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Post-init API used by {@link ActivityLifecycleIntegration} for standalone app-start tracing. */
@ApiStatus.Internal
public interface StandaloneAppStartCoordinator {

  @Nullable
  SentryId getReusableTraceId();

  void setReusableTraceId(final @Nullable SentryId traceId);

  @NotNull
  ITransaction startForActivity(
      final @NotNull SentryId traceId,
      final @NotNull String activityName,
      final @NotNull SentryDate appStartTime,
      final @Nullable TracesSamplingDecision samplingDecision);

  @Nullable
  ISpan getAppStartTransaction();

  void finishAppStart(final @NotNull SentryDate endDate);

  void onActivityDestroyed();
}
