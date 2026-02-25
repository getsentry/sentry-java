package io.sentry.android.core;

import android.app.ApplicationStartInfo;
import android.os.SystemClock;
import androidx.annotation.RequiresApi;
import io.sentry.IScopes;
import io.sentry.SentryDate;
import io.sentry.SentryNanotimeDate;
import io.sentry.SpanContext;
import io.sentry.SpanDataConvention;
import io.sentry.SpanId;
import io.sentry.SpanStatus;
import io.sentry.TracesSamplingDecision;
import io.sentry.android.core.internal.util.AndroidThreadChecker;
import io.sentry.android.core.performance.ActivityLifecycleTimeSpan;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.TimeSpan;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentrySpan;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.TransactionInfo;
import io.sentry.protocol.TransactionNameSource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Processor that generates app.start transactions from ApplicationStartInfo data.
 *
 * <p>This processor creates a transaction with spans representing the app startup timeline,
 * including bind_application, content provider initialization, application onCreate, activity
 * lifecycle, TTID, and TTFD.
 *
 * <p>Requires API level 35 (Android 15) or higher.
 */
@RequiresApi(api = 35)
final class ApplicationStartInfoTracingProcessor implements IApplicationStartInfoProcessor {

  private final @NotNull SentryAndroidOptions options;

  ApplicationStartInfoTracingProcessor(final @NotNull SentryAndroidOptions options) {
    this.options = options;
  }

  @Override
  public void process(
      final @NotNull ApplicationStartInfo startInfo,
      final @NotNull Map<String, String> tags,
      final @NotNull IScopes scopes) {

    final long currentUnixMs = System.currentTimeMillis();
    final long currentRealtimeMs = SystemClock.elapsedRealtime();
    final long unixTimeOffsetMs = currentUnixMs - currentRealtimeMs;

    final long startRealtimeMs = getStartTimestampMs(startInfo);
    final long ttidRealtimeMs = getFirstFrameTimestampMs(startInfo);
    final long ttfdRealtimeMs = getFullyDrawnTimestampMs(startInfo);
    final long bindApplicationRealtimeMs = getBindApplicationTimestampMs(startInfo);

    final SentryDate startDate = dateFromUnixTime(unixTimeOffsetMs + startRealtimeMs);
    final long endTimestamp = ttidRealtimeMs > 0 ? ttidRealtimeMs : ttfdRealtimeMs;
    final SentryDate endDate =
        endTimestamp > 0
            ? dateFromUnixTime(unixTimeOffsetMs + endTimestamp)
            : options.getDateProvider().now();

    final SentryId traceId = new SentryId();
    final SpanId spanId = new SpanId();
    final SpanContext traceContext =
        new SpanContext(traceId, spanId, "app.start", null, new TracesSamplingDecision(true));
    traceContext.setStatus(SpanStatus.OK);

    final double startTimestampSecs = dateToSeconds(startDate);
    final double endTimestampSecs = dateToSeconds(endDate);

    final SentryTransaction transaction =
        new SentryTransaction(
            "app.start",
            startTimestampSecs,
            endTimestampSecs,
            new ArrayList<>(),
            new HashMap<>(),
            new TransactionInfo(TransactionNameSource.COMPONENT.apiName()));

    transaction.getContexts().setTrace(traceContext);

    for (Map.Entry<String, String> entry : tags.entrySet()) {
      transaction.setTag(entry.getKey(), entry.getValue());
    }

    if (bindApplicationRealtimeMs > 0) {
      transaction
          .getSpans()
          .add(
              createSpan(
                  traceId,
                  spanId,
                  "bind_application",
                  null,
                  startDate,
                  dateFromUnixTime(unixTimeOffsetMs + bindApplicationRealtimeMs)));
    }

    if (startInfo.getStartType() == ApplicationStartInfo.START_TYPE_COLD) {
      attachColdStartInstrumentations(transaction, traceId, spanId);
    }

    attachActivitySpans(transaction, traceId, spanId);

    if (ttidRealtimeMs > 0) {
      transaction
          .getSpans()
          .add(
              createSpan(
                  traceId,
                  spanId,
                  "ttid",
                  null,
                  startDate,
                  dateFromUnixTime(unixTimeOffsetMs + ttidRealtimeMs)));
    }
    if (ttfdRealtimeMs > 0) {
      transaction
          .getSpans()
          .add(
              createSpan(
                  traceId,
                  spanId,
                  "ttfd",
                  null,
                  startDate,
                  dateFromUnixTime(unixTimeOffsetMs + ttfdRealtimeMs)));
    }

    scopes.captureTransaction(transaction, null, null);
  }

  private @NotNull SentrySpan createSpan(
      final @NotNull SentryId traceId,
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final @Nullable String description,
      final @NotNull SentryDate startDate,
      final @NotNull SentryDate endDate) {

    final Map<String, Object> spanData = new HashMap<>();
    spanData.put(SpanDataConvention.THREAD_ID, AndroidThreadChecker.mainThreadSystemId);
    spanData.put(SpanDataConvention.THREAD_NAME, "main");

    final double startTimestampSecs = dateToSeconds(startDate);
    final double endTimestampSecs = dateToSeconds(endDate);

    return new SentrySpan(
        startTimestampSecs,
        endTimestampSecs,
        traceId,
        new SpanId(),
        parentSpanId,
        operation,
        description,
        SpanStatus.OK,
        "manual",
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        spanData);
  }

  private void attachColdStartInstrumentations(
      final @NotNull SentryTransaction transaction,
      final @NotNull SentryId traceId,
      final @NotNull SpanId parentSpanId) {

    final @NotNull AppStartMetrics appStartMetrics = AppStartMetrics.getInstance();
    final @NotNull List<TimeSpan> contentProviderSpans =
        appStartMetrics.getContentProviderOnCreateTimeSpans();

    for (final TimeSpan cpSpan : contentProviderSpans) {
      if (cpSpan.hasStarted() && cpSpan.hasStopped()) {
        final SentryDate cpStartDate = dateFromUnixTime(cpSpan.getStartTimestampMs());
        final SentryDate cpEndDate = dateFromUnixTime(cpSpan.getProjectedStopTimestampMs());

        transaction
            .getSpans()
            .add(
                createSpan(
                    traceId,
                    parentSpanId,
                    "contentprovider.on_create",
                    cpSpan.getDescription(),
                    cpStartDate,
                    cpEndDate));
      }
    }

    final TimeSpan appOnCreateSpan = appStartMetrics.getApplicationOnCreateTimeSpan();
    final String appOnCreateDescription =
        appOnCreateSpan.hasStarted() ? appOnCreateSpan.getDescription() : null;

    if (appOnCreateSpan.hasStarted() && appOnCreateSpan.hasStopped()) {
      final SentryDate appOnCreateStart = dateFromUnixTime(appOnCreateSpan.getStartTimestampMs());
      final SentryDate appOnCreateEnd =
          dateFromUnixTime(appOnCreateSpan.getProjectedStopTimestampMs());

      transaction
          .getSpans()
          .add(
              createSpan(
                  traceId,
                  parentSpanId,
                  "application.on_create",
                  appOnCreateDescription,
                  appOnCreateStart,
                  appOnCreateEnd));
    }
  }

  private void attachActivitySpans(
      final @NotNull SentryTransaction transaction,
      final @NotNull SentryId traceId,
      final @NotNull SpanId parentSpanId) {

    final @NotNull AppStartMetrics appStartMetrics = AppStartMetrics.getInstance();
    final @NotNull List<ActivityLifecycleTimeSpan> activityLifecycleTimeSpans =
        appStartMetrics.getActivityLifecycleTimeSpans();

    for (final ActivityLifecycleTimeSpan span : activityLifecycleTimeSpans) {
      final TimeSpan onCreate = span.getOnCreate();
      final TimeSpan onStart = span.getOnStart();

      if (onCreate.hasStarted() && onCreate.hasStopped()) {
        final SentryDate start = dateFromUnixTime(onCreate.getStartTimestampMs());
        final SentryDate end = dateFromUnixTime(onCreate.getProjectedStopTimestampMs());

        transaction
            .getSpans()
            .add(
                createSpan(
                    traceId,
                    parentSpanId,
                    "activity.on_create",
                    onCreate.getDescription(),
                    start,
                    end));
      }

      if (onStart.hasStarted() && onStart.hasStopped()) {
        final SentryDate start = dateFromUnixTime(onStart.getStartTimestampMs());
        final SentryDate end = dateFromUnixTime(onStart.getProjectedStopTimestampMs());

        transaction
            .getSpans()
            .add(
                createSpan(
                    traceId,
                    parentSpanId,
                    "activity.on_start",
                    onStart.getDescription(),
                    start,
                    end));
      }
    }
  }

  private static double dateToSeconds(final @NotNull SentryDate date) {
    return date.nanoTimestamp() / 1e9;
  }

  private static @NotNull SentryDate dateFromUnixTime(final long timeMillis) {
    return new SentryNanotimeDate(new Date(timeMillis), 0);
  }

  private long getStartTimestampMs(final @NotNull ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();
    final Long forkTime = timestamps.get(ApplicationStartInfo.START_TIMESTAMP_FORK);
    return forkTime != null ? TimeUnit.NANOSECONDS.toMillis(forkTime) : 0;
  }

  private long getBindApplicationTimestampMs(final @NotNull ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();
    final Long bindTime = timestamps.get(ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION);
    return bindTime != null ? TimeUnit.NANOSECONDS.toMillis(bindTime) : 0;
  }

  private long getFirstFrameTimestampMs(final @NotNull ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();
    final Long firstFrameTime = timestamps.get(ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME);
    return firstFrameTime != null ? TimeUnit.NANOSECONDS.toMillis(firstFrameTime) : 0;
  }

  private long getFullyDrawnTimestampMs(final @NotNull ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();
    final Long fullyDrawnTime = timestamps.get(ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN);
    return fullyDrawnTime != null ? TimeUnit.NANOSECONDS.toMillis(fullyDrawnTime) : 0;
  }
}
