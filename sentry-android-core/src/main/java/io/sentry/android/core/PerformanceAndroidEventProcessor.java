package io.sentry.android.core;

import static io.sentry.android.core.ActivityLifecycleIntegration.APP_START_COLD;
import static io.sentry.android.core.ActivityLifecycleIntegration.APP_START_WARM;
import static io.sentry.android.core.ActivityLifecycleIntegration.UI_LOAD_OP;

import android.os.Looper;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.MeasurementUnit;
import io.sentry.SentryEvent;
import io.sentry.SpanContext;
import io.sentry.SpanDataConvention;
import io.sentry.SpanId;
import io.sentry.SpanStatus;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.TimeSpan;
import io.sentry.protocol.App;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentrySpan;
import io.sentry.protocol.SentryTransaction;
import io.sentry.util.Objects;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Event Processor responsible for adding Android metrics to transactions */
final class PerformanceAndroidEventProcessor implements EventProcessor {

  private static final String APP_METRICS_ORIGIN = "auto.ui";

  private static final String APP_METRICS_CONTENT_PROVIDER_OP = "contentprovider.load";
  private static final String APP_METRICS_ACTIVITIES_OP = "activity.load";
  private static final String APP_METRICS_APPLICATION_OP = "application.load";
  private static final String APP_METRICS_PROCESS_INIT_OP = "process.load";
  private static final long MAX_PROCESS_INIT_APP_START_DIFF_MS = 10000;

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

    final @NotNull AppStartMetrics appStartMetrics = AppStartMetrics.getInstance();
    // the app start measurement is only sent once and only if the transaction has
    // the app.start span, which is automatically created by the SDK.
    if (hasAppStartSpan(transaction)) {
      if (appStartMetrics.shouldSendStartMeasurements()) {
        final @NotNull TimeSpan appStartTimeSpan =
            appStartMetrics.getAppStartTimeSpanWithFallback(options);
        final long appStartUpDurationMs = appStartTimeSpan.getDurationMs();

        // if appStartUpDurationMs is 0, metrics are not ready to be sent
        if (appStartUpDurationMs != 0) {
          final MeasurementValue value =
              new MeasurementValue(
                  (float) appStartUpDurationMs, MeasurementUnit.Duration.MILLISECOND.apiName());

          final String appStartKey =
              appStartMetrics.getAppStartType() == AppStartMetrics.AppStartType.COLD
                  ? MeasurementValue.KEY_APP_START_COLD
                  : MeasurementValue.KEY_APP_START_WARM;

          transaction.getMeasurements().put(appStartKey, value);

          attachAppStartSpans(appStartMetrics, transaction);
          appStartMetrics.onAppStartSpansSent();
        }
      }

      @Nullable App appContext = transaction.getContexts().getApp();
      if (appContext == null) {
        appContext = new App();
        transaction.getContexts().setApp(appContext);
      }
      final String appStartType =
          appStartMetrics.getAppStartType() == AppStartMetrics.AppStartType.COLD ? "cold" : "warm";
      appContext.setStartType(appStartType);
    }

    setContributingFlags(transaction);

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

  private void setContributingFlags(SentryTransaction transaction) {

    @Nullable SentrySpan ttidSpan = null;
    @Nullable SentrySpan ttfdSpan = null;
    for (final @NotNull SentrySpan span : transaction.getSpans()) {
      if (ActivityLifecycleIntegration.TTID_OP.equals(span.getOp())) {
        ttidSpan = span;
      } else if (ActivityLifecycleIntegration.TTFD_OP.equals(span.getOp())) {
        ttfdSpan = span;
      }
      // once both are found we can early exit
      if (ttidSpan != null && ttfdSpan != null) {
        break;
      }
    }

    if (ttidSpan == null && ttfdSpan == null) {
      return;
    }

    for (final @NotNull SentrySpan span : transaction.getSpans()) {
      // as ttid and ttfd spans are artificially created, we don't want to set the flags on them
      if (span == ttidSpan || span == ttfdSpan) {
        continue;
      }

      // let's assume main thread, unless it's set differently
      boolean spanOnMainThread = true;
      final @Nullable Map<String, Object> spanData = span.getData();
      if (spanData != null) {
        final @Nullable Object threadName = spanData.get(SpanDataConvention.THREAD_NAME);
        spanOnMainThread = threadName == null || "main".equals(threadName);
      }

      // for ttid, only main thread spans are relevant
      final boolean withinTtid =
          (ttidSpan != null)
              && isTimestampWithinSpan(span.getStartTimestamp(), ttidSpan)
              && spanOnMainThread;

      final boolean withinTtfd =
          (ttfdSpan != null) && isTimestampWithinSpan(span.getStartTimestamp(), ttfdSpan);

      if (withinTtid || withinTtfd) {
        @Nullable Map<String, Object> data = span.getData();
        if (data == null) {
          data = new ConcurrentHashMap<>();
          span.setData(data);
        }
        if (withinTtid) {
          data.put(SpanDataConvention.CONTRIBUTES_TTID, true);
        }
        if (withinTtfd) {
          data.put(SpanDataConvention.CONTRIBUTES_TTFD, true);
        }
      }
    }
  }

  private static boolean isTimestampWithinSpan(
      final double timestamp, final @NotNull SentrySpan target) {
    return timestamp >= target.getStartTimestamp()
        && (target.getTimestamp() == null || timestamp <= target.getTimestamp());
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

  private void attachAppStartSpans(
      final @NotNull AppStartMetrics appStartMetrics, final @NotNull SentryTransaction txn) {

    // We include process init, content providers and application.onCreate spans only on cold start
    if (appStartMetrics.getAppStartType() != AppStartMetrics.AppStartType.COLD) {
      return;
    }

    final @Nullable SpanContext traceContext = txn.getContexts().getTrace();
    if (traceContext == null) {
      return;
    }
    final @NotNull SentryId traceId = traceContext.getTraceId();

    // determine the app.start.cold span, where all other spans will be attached to
    @Nullable SpanId parentSpanId = null;
    final @NotNull List<SentrySpan> spans = txn.getSpans();
    for (final @NotNull SentrySpan span : spans) {
      if (span.getOp().contentEquals(APP_START_COLD)) {
        parentSpanId = span.getSpanId();
        break;
      }
    }

    // Process init
    final @NotNull TimeSpan processInitTimeSpan = appStartMetrics.createProcessInitSpan();
    if (processInitTimeSpan.hasStarted()
        && processInitTimeSpan.getDurationMs() <= MAX_PROCESS_INIT_APP_START_DIFF_MS) {
      txn.getSpans()
          .add(
              timeSpanToSentrySpan(
                  processInitTimeSpan, parentSpanId, traceId, APP_METRICS_PROCESS_INIT_OP));
    }

    // Content Providers
    final @NotNull List<TimeSpan> contentProviderOnCreates =
        appStartMetrics.getContentProviderOnCreateTimeSpans();
    if (!contentProviderOnCreates.isEmpty()) {
      for (final @NotNull TimeSpan contentProvider : contentProviderOnCreates) {
        txn.getSpans()
            .add(
                timeSpanToSentrySpan(
                    contentProvider, parentSpanId, traceId, APP_METRICS_CONTENT_PROVIDER_OP));
      }
    }

    // Application.onCreate
    final @NotNull TimeSpan appOnCreate = appStartMetrics.getApplicationOnCreateTimeSpan();
    if (appOnCreate.hasStopped()) {
      txn.getSpans()
          .add(
              timeSpanToSentrySpan(appOnCreate, parentSpanId, traceId, APP_METRICS_APPLICATION_OP));
    }
  }

  @NotNull
  private static SentrySpan timeSpanToSentrySpan(
      final @NotNull TimeSpan span,
      final @Nullable SpanId parentSpanId,
      final @NotNull SentryId traceId,
      final @NotNull String operation) {

    final Map<String, Object> defaultSpanData = new HashMap<>(2);
    defaultSpanData.put(SpanDataConvention.THREAD_ID, Looper.getMainLooper().getThread().getId());
    defaultSpanData.put(SpanDataConvention.THREAD_NAME, "main");

    defaultSpanData.put(SpanDataConvention.CONTRIBUTES_TTID, true);
    defaultSpanData.put(SpanDataConvention.CONTRIBUTES_TTFD, true);

    return new SentrySpan(
        span.getStartTimestampSecs(),
        span.getProjectedStopTimestampSecs(),
        traceId,
        new SpanId(),
        parentSpanId,
        operation,
        span.getDescription(),
        SpanStatus.OK,
        APP_METRICS_ORIGIN,
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        null,
        defaultSpanData);
  }
}
