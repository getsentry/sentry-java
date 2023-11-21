package io.sentry.android.core;

import static io.sentry.android.core.ActivityLifecycleIntegration.APP_START_COLD;
import static io.sentry.android.core.ActivityLifecycleIntegration.APP_START_WARM;
import static io.sentry.android.core.ActivityLifecycleIntegration.UI_LOAD_OP;

import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.MeasurementUnit;
import io.sentry.SentryEvent;
import io.sentry.SpanContext;
import io.sentry.SpanId;
import io.sentry.SpanStatus;
import io.sentry.android.core.performance.ActivityLifecycleTimeSpan;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.TimeSpan;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentrySpan;
import io.sentry.protocol.SentryTransaction;
import io.sentry.util.Objects;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Event Processor responsible for adding Android metrics to transactions */
final class PerformanceAndroidEventProcessor implements EventProcessor {

  private static final String APP_METRICS_ORIGN = "auto.ui";
  private boolean sentStartMeasurement = false;

  private final @NotNull ActivityFramesTracker activityFramesTracker;
  private final @NotNull SentryAndroidOptions options;

  PerformanceAndroidEventProcessor(
      final @NotNull SentryAndroidOptions options,
      final @NotNull ActivityFramesTracker activityFramesTracker) {
    this.options = Objects.requireNonNull(options, "SentryAndroidOptions is required");
    this.activityFramesTracker =
        Objects.requireNonNull(activityFramesTracker, "ActivityFramesTracker is required");
  }

  /**
   * Returns the event itself
   *
   * @param event the SentryEvent the SentryEvent
   * @param hint the Hint the Hint
   * @return returns the event itself
   */
  @Override
  @Nullable
  public SentryEvent process(@NotNull SentryEvent event, @NotNull Hint hint) {
    // that's only necessary because on newer versions of Unity, if not overriding this method, it's
    // throwing 'java.lang.AbstractMethodError: abstract method' and the reason is probably
    // compilation mismatch.
    return event;
  }

  @SuppressWarnings("NullAway")
  @Override
  public synchronized @NotNull SentryTransaction process(
      @NotNull SentryTransaction transaction, @NotNull Hint hint) {

    if (!options.isTracingEnabled()) {
      return transaction;
    }

    // the app start measurement is only sent once and only if the transaction has
    // the app.start span, which is automatically created by the SDK.
    if (!sentStartMeasurement && hasAppStartSpan(transaction)) {
      final @NotNull TimeSpan appStartTimeSpan =
          options.isEnableStarfish()
              ? AppStartMetrics.getInstance().getAppStartTimeSpan()
              : AppStartMetrics.getInstance().getLegacyAppStartTimeSpan();
      final long appStartUpInterval = appStartTimeSpan.getDurationMs();

      // if appStartUpInterval is 0, metrics are not ready to be sent
      if (appStartUpInterval != 0) {
        final MeasurementValue value =
            new MeasurementValue(
                (float) appStartUpInterval, MeasurementUnit.Duration.MILLISECOND.apiName());

        final String appStartKey =
            AppStartMetrics.getInstance().getAppStartType() == AppStartMetrics.AppStartType.COLD
                ? MeasurementValue.KEY_APP_START_COLD
                : MeasurementValue.KEY_APP_START_WARM;

        transaction.getMeasurements().put(appStartKey, value);

        attachColdAppStartSpans(AppStartMetrics.getInstance(), transaction);
        sentStartMeasurement = true;
      }
    }

    final SentryId eventId = transaction.getEventId();
    final SpanContext spanContext = transaction.getContexts().getTrace();

    // only add slow/frozen frames to transactions created by ActivityLifecycleIntegration
    // which have the operation UI_LOAD_OP. If a user-defined (or hybrid SDK) transaction
    // users it, we'll also add the metrics if available
    if (eventId != null
        && spanContext != null
        && spanContext.getOperation().contentEquals(UI_LOAD_OP)) {
      final Map<String, @NotNull MeasurementValue> framesMetrics =
          activityFramesTracker.takeMetrics(eventId);
      if (framesMetrics != null) {
        transaction.getMeasurements().putAll(framesMetrics);
      }
    }

    return transaction;
  }

  private boolean hasAppStartSpan(final @NotNull SentryTransaction txn) {
    final @NotNull List<SentrySpan> spans = txn.getSpans();
    for (final @NotNull SentrySpan span : spans) {
      if (span.getOp().contentEquals(APP_START_COLD)
          || span.getOp().contentEquals(APP_START_WARM)) {
        return true;
      }
    }

    final @Nullable SpanContext context = txn.getContexts().getTrace();
    return context != null
        && (context.getOperation().equals(APP_START_COLD)
            || context.getOperation().equals(APP_START_WARM));
  }

  private void attachColdAppStartSpans(
      final @NotNull AppStartMetrics appStartMetrics, final @NotNull SentryTransaction txn) {

    // data will be filled anyway only for cold app starts
    if (appStartMetrics.getAppStartType() != AppStartMetrics.AppStartType.COLD) {
      return;
    }

    final @Nullable SpanContext traceContext = txn.getContexts().getTrace();
    if (traceContext == null) {
      return;
    }
    final @NotNull SentryId traceId = traceContext.getTraceId();

    // Application.onCreate
    final @NotNull TimeSpan appOnCreate = appStartMetrics.getApplicationOnCreateTimeSpan();
    if (appOnCreate.hasStopped()) {
      final SentrySpan span =
          new SentrySpan(
              appOnCreate.getStartTimestampS(),
              appOnCreate.getProjectedStopTimestampS(),
              traceId,
              new SpanId(),
              null,
              UI_LOAD_OP,
              appOnCreate.getDescription(),
              SpanStatus.OK,
              APP_METRICS_ORIGN,
              new HashMap<>(),
              null);
      txn.getSpans().add(span);
    }

    // Content Provider
    final @NotNull List<TimeSpan> contentProviderOnCreates =
        appStartMetrics.getContentProviderOnCreateTimeSpans();
    if (!contentProviderOnCreates.isEmpty()) {
      final @NotNull SentrySpan contentProviderRootSpan =
          new SentrySpan(
              contentProviderOnCreates.get(0).getStartTimestampS(),
              contentProviderOnCreates
                  .get(contentProviderOnCreates.size() - 1)
                  .getProjectedStopTimestampS(),
              traceId,
              new SpanId(),
              null,
              UI_LOAD_OP,
              "ContentProvider",
              SpanStatus.OK,
              APP_METRICS_ORIGN,
              new HashMap<>(),
              null);
      txn.getSpans().add(contentProviderRootSpan);
      for (final @NotNull TimeSpan contentProvider : contentProviderOnCreates) {
        final SentrySpan contentProviderSpan =
            new SentrySpan(
                contentProvider.getStartTimestampS(),
                contentProvider.getProjectedStopTimestampS(),
                traceId,
                new SpanId(),
                contentProviderRootSpan.getSpanId(),
                UI_LOAD_OP,
                contentProvider.getDescription(),
                SpanStatus.OK,
                APP_METRICS_ORIGN,
                new HashMap<>(),
                null);
        txn.getSpans().add(contentProviderSpan);
      }
    }

    // Activities
    final @NotNull List<ActivityLifecycleTimeSpan> activityLifecycleTimeSpans =
        appStartMetrics.getActivityLifecycleTimeSpans();
    if (!activityLifecycleTimeSpans.isEmpty()) {
      final SentrySpan activityRootSpan =
          new SentrySpan(
              activityLifecycleTimeSpans.get(0).onCreate.getStartTimestampS(),
              activityLifecycleTimeSpans
                  .get(activityLifecycleTimeSpans.size() - 1)
                  .onStart
                  .getProjectedStopTimestampS(),
              traceId,
              new SpanId(),
              null,
              UI_LOAD_OP,
              "Activity",
              SpanStatus.OK,
              APP_METRICS_ORIGN,
              new HashMap<>(),
              null);
      txn.getSpans().add(activityRootSpan);

      for (ActivityLifecycleTimeSpan activityTimeSpan : activityLifecycleTimeSpans) {
        if (activityTimeSpan.onCreate.hasStarted() && activityTimeSpan.onCreate.hasStopped()) {
          final SentrySpan onCreateSpan =
              new SentrySpan(
                  activityTimeSpan.onCreate.getStartTimestampS(),
                  activityTimeSpan.onCreate.getProjectedStopTimestampS(),
                  traceId,
                  new SpanId(),
                  activityRootSpan.getSpanId(),
                  UI_LOAD_OP,
                  activityTimeSpan.onCreate.getDescription(),
                  SpanStatus.OK,
                  APP_METRICS_ORIGN,
                  new HashMap<>(),
                  null);
          txn.getSpans().add(onCreateSpan);
        }
        if (activityTimeSpan.onStart.hasStarted() && activityTimeSpan.onStart.hasStopped()) {
          final SentrySpan onStartSpan =
              new SentrySpan(
                  activityTimeSpan.onStart.getStartTimestampS(),
                  activityTimeSpan.onStart.getProjectedStopTimestampS(),
                  traceId,
                  new SpanId(),
                  activityRootSpan.getSpanId(),
                  UI_LOAD_OP,
                  activityTimeSpan.onStart.getDescription(),
                  SpanStatus.OK,
                  APP_METRICS_ORIGN,
                  new HashMap<>(),
                  null);
          txn.getSpans().add(onStartSpan);
        }
      }
    }
  }
}
