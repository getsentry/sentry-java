package io.sentry.android.core;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ApplicationStartInfo;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import androidx.annotation.RequiresApi;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.Integration;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryNanotimeDate;
import io.sentry.SentryOptions;
import io.sentry.SpanContext;
import io.sentry.SpanDataConvention;
import io.sentry.SpanId;
import io.sentry.SpanStatus;
import io.sentry.android.core.internal.util.AndroidThreadChecker;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.TimeSpan;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentrySpan;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.TransactionInfo;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.IntegrationUtils;
import java.io.Closeable;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ApplicationStartInfoIntegration implements Integration, Closeable {

  private final @NotNull Context context;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull AutoClosableReentrantLock startLock = new AutoClosableReentrantLock();
  private boolean isClosed;

  public ApplicationStartInfoIntegration(
      final @NotNull Context context, final @NotNull BuildInfoProvider buildInfoProvider) {
    this.context = ContextUtils.getApplicationContext(context);
    this.buildInfoProvider =
        java.util.Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
  }

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    register(scopes, (SentryAndroidOptions) options);
  }

  @SuppressLint("NewApi")
  private void register(
      final @NotNull IScopes scopes, final @NotNull SentryAndroidOptions options) {
    options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "ApplicationStartInfoIntegration enabled: %s",
            options.isEnableApplicationStartInfo());

    //    if (!options.isEnableApplicationStartInfo()) {
    //      return;
    //    }

    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "ApplicationStartInfo requires API level 35+. Current: %d",
              buildInfoProvider.getSdkInfoVersion());
      return;
    }

    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                try (final ISentryLifecycleToken ignored = startLock.acquire()) {
                  if (!isClosed) {
                    registerAppStartListener(scopes, options);
                  }
                }
              });
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Failed to start ApplicationStartInfoIntegration.", e);
    }

    IntegrationUtils.addIntegrationToSdkVersion("ApplicationStartInfo");
  }

  @RequiresApi(api = 35)
  private void registerAppStartListener(
      final @NotNull IScopes scopes, final @NotNull SentryAndroidOptions options) {
    final ActivityManager activityManager =
        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

    if (activityManager == null) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to retrieve ActivityManager.");
      return;
    }

    try {
      final Executor executor =
          new Executor() {
            @Override
            public void execute(Runnable command) {
              options.getExecutorService().submit(command);
            }
          };

      activityManager.addApplicationStartInfoCompletionListener(
          executor,
          startInfo -> {
            try {
              onApplicationStartInfoAvailable(startInfo, scopes, options);
            } catch (Throwable e) {
              options
                  .getLogger()
                  .log(SentryLevel.ERROR, "Error reporting ApplicationStartInfo.", e);
            }
          });

      options
          .getLogger()
          .log(SentryLevel.DEBUG, "ApplicationStartInfo completion listener registered.");
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to register ApplicationStartInfo listener.", e);
    }
  }

  @RequiresApi(api = 35)
  private void onApplicationStartInfoAvailable(
      final @NotNull ApplicationStartInfo startInfo,
      final @NotNull IScopes scopes,
      final @NotNull SentryAndroidOptions options) {

    final long currentUnixMs = System.currentTimeMillis();
    final long currentRealtimeMs = SystemClock.elapsedRealtime();
    final long unixTimeOffsetMs = currentUnixMs - currentRealtimeMs;

    final Map<String, String> tags = extractTags(startInfo);
    final String transactionName = "app.start." + getReasonLabel(startInfo.getReason());
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

    // Create trace context
    final SentryId traceId = new SentryId();
    final SpanId spanId = new SpanId();
    final SpanContext traceContext =
        new SpanContext(traceId, spanId, "app.startDate.info", null, null);
    traceContext.setStatus(SpanStatus.OK);

    // Convert timestamps to seconds
    final double startTimestampSecs = dateToSeconds(startDate);
    final double endTimestampSecs = dateToSeconds(endDate);

    // Create transaction directly
    final SentryTransaction transaction =
        new SentryTransaction(
            transactionName,
            startTimestampSecs,
            endTimestampSecs,
            new java.util.ArrayList<>(),
            new HashMap<>(),
            new TransactionInfo(TransactionNameSource.COMPONENT.apiName()));

    // Set trace context
    transaction.getContexts().setTrace(traceContext);

    // Set tags
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      transaction.setTag(entry.getKey(), entry.getValue());
    }

    // Add spans
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

    attachAppStartMetrics(transaction, traceId, spanId, unixTimeOffsetMs);

    // if application instrumentation was disabled, report app start info data
    final TimeSpan appOnCreateSpan = AppStartMetrics.getInstance().getApplicationOnCreateTimeSpan();
    if (!appOnCreateSpan.hasStarted() || appOnCreateSpan.hasStopped()) {
      final long applicationOnCreateRealtimeMs = getApplicationOnCreateTimestampMs(startInfo);
      if (applicationOnCreateRealtimeMs > 0) {
        transaction
            .getSpans()
            .add(
                createSpan(
                    traceId,
                    spanId,
                    "application.onCreate",
                    null,
                    startDate,
                    dateFromUnixTime(unixTimeOffsetMs + applicationOnCreateRealtimeMs)));
      }
    }

    scopes.captureTransaction(transaction, null, null);
  }

  private @NotNull SentrySpan createSpan(
      final @NotNull SentryId traceId,
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final String description,
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

  private static double dateToSeconds(final @NotNull SentryDate date) {
    return date.nanoTimestamp() / 1e9;
  }

  @RequiresApi(api = 35)
  private void attachAppStartMetrics(
      final @NotNull SentryTransaction transaction,
      final @NotNull SentryId traceId,
      final @NotNull SpanId parentSpanId,
      final long unixTimeOffsetMs) {

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
                    "contentprovider.load",
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
                  "application.onCreate",
                  appOnCreateDescription,
                  appOnCreateStart,
                  appOnCreateEnd));
    }
  }

  @RequiresApi(api = 35)
  private @NotNull Map<String, String> extractTags(
      final @NotNull android.app.ApplicationStartInfo startInfo) {
    final Map<String, String> tags = new HashMap<>();
    tags.put("start.reason", getReasonLabel(startInfo.getReason()));
    tags.put("start.type", getStartupTypeLabel(startInfo.getStartType()));
    tags.put("start.launch_mode", getLaunchModeLabel(startInfo.getLaunchMode()));
    return tags;
  }

  @RequiresApi(api = 35)
  private @NotNull String getStartupTypeLabel(final int startType) {
    switch (startType) {
      case ApplicationStartInfo.START_TYPE_COLD:
        return "cold";
      case ApplicationStartInfo.START_TYPE_WARM:
        return "warm";
      case ApplicationStartInfo.START_TYPE_HOT:
        return "hot";
      default:
        return "unknown";
    }
  }

  @RequiresApi(api = 35)
  private @NotNull String getLaunchModeLabel(final int launchMode) {
    switch (launchMode) {
      case ApplicationStartInfo.LAUNCH_MODE_STANDARD:
        return "standard";
      case ApplicationStartInfo.LAUNCH_MODE_SINGLE_TOP:
        return "single_top";
      case ApplicationStartInfo.LAUNCH_MODE_SINGLE_INSTANCE:
        return "single_instance";
      case ApplicationStartInfo.LAUNCH_MODE_SINGLE_TASK:
        return "single_task";
      case ApplicationStartInfo.LAUNCH_MODE_SINGLE_INSTANCE_PER_TASK:
        return "single_instance_per_task";
      default:
        return "unknown";
    }
  }

  @RequiresApi(api = 35)
  private @NotNull String getReasonLabel(final int reason) {
    switch (reason) {
      case ApplicationStartInfo.START_REASON_ALARM:
        return "alarm";
      case ApplicationStartInfo.START_REASON_BACKUP:
        return "backup";
      case ApplicationStartInfo.START_REASON_BOOT_COMPLETE:
        return "boot_complete";
      case ApplicationStartInfo.START_REASON_BROADCAST:
        return "broadcast";
      case ApplicationStartInfo.START_REASON_CONTENT_PROVIDER:
        return "content_provider";
      case ApplicationStartInfo.START_REASON_JOB:
        return "job";
      case ApplicationStartInfo.START_REASON_LAUNCHER:
        return "launcher";
      case ApplicationStartInfo.START_REASON_OTHER:
        return "other";
      case ApplicationStartInfo.START_REASON_PUSH:
        return "push";
      case ApplicationStartInfo.START_REASON_SERVICE:
        return "service";
      case ApplicationStartInfo.START_REASON_START_ACTIVITY:
        return "start_activity";
      default:
        return "unknown";
    }
  }

  @RequiresApi(api = 35)
  private long getStartTimestampMs(final @NotNull android.app.ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();
    final Long forkTime = timestamps.get(ApplicationStartInfo.START_TIMESTAMP_FORK);
    return forkTime != null ? TimeUnit.NANOSECONDS.toMillis(forkTime) : 0;
  }

  @RequiresApi(api = 35)
  private long getBindApplicationTimestampMs(
      final @NotNull android.app.ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();

    final Long bindTime = timestamps.get(ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION);
    return bindTime != null ? TimeUnit.NANOSECONDS.toMillis(bindTime) : 0;
  }

  @RequiresApi(api = 35)
  private long getApplicationOnCreateTimestampMs(
      final @NotNull android.app.ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();
    final Long onCreateTime =
        timestamps.get(ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE);
    return onCreateTime != null ? TimeUnit.NANOSECONDS.toMillis(onCreateTime) : 0;
  }

  @RequiresApi(api = 35)
  private long getFirstFrameTimestampMs(final @NotNull android.app.ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();
    final Long firstFrameTime = timestamps.get(ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME);
    return firstFrameTime != null ? TimeUnit.NANOSECONDS.toMillis(firstFrameTime) : 0;
  }

  @RequiresApi(api = 35)
  private long getFullyDrawnTimestampMs(final @NotNull android.app.ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();
    final Long fullyDrawnTime = timestamps.get(ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN);
    return fullyDrawnTime != null ? TimeUnit.NANOSECONDS.toMillis(fullyDrawnTime) : 0;
  }

  @Override
  public void close() throws IOException {
    try (final ISentryLifecycleToken ignored = startLock.acquire()) {
      isClosed = true;
    }
  }

  private static @NotNull SentryDate dateFromUnixTime(final long timeMillis) {
    return new SentryNanotimeDate(new Date(timeMillis), 0);
  }
}
