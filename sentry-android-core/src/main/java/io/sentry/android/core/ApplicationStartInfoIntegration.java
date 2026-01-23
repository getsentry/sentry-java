package io.sentry.android.core;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import androidx.annotation.RequiresApi;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ITransaction;
import io.sentry.Integration;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryNanotimeDate;
import io.sentry.SentryOptions;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.TimeSpan;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.IntegrationUtils;
import java.io.Closeable;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ApplicationStartInfoIntegration implements Integration, Closeable {

  private final @NotNull Context context;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull AutoClosableReentrantLock startLock = new AutoClosableReentrantLock();
  private @Nullable SentryAndroidOptions options;
  private @Nullable IScopes scopes;
  private boolean isClosed = false;

  public ApplicationStartInfoIntegration(
      final @NotNull Context context, final @NotNull BuildInfoProvider buildInfoProvider) {
    this.context = ContextUtils.getApplicationContext(context);
    this.buildInfoProvider =
        java.util.Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
  }

  @Override
  public final void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    register(scopes, (SentryAndroidOptions) options);
  }

  private void register(
      final @NotNull IScopes scopes, final @NotNull SentryAndroidOptions options) {
    this.scopes = java.util.Objects.requireNonNull(scopes, "Scopes are required");
    this.options = java.util.Objects.requireNonNull(options, "SentryAndroidOptions is required");

    options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "ApplicationStartInfoIntegration enabled: %s",
            options.isEnableApplicationStartInfo());

    if (!options.isEnableApplicationStartInfo()) {
      return;
    }

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
      // Wrap ISentryExecutorService as Executor for Android API
      final java.util.concurrent.Executor executor = options.getExecutorService()::submit;

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
      final @NotNull android.app.ApplicationStartInfo startInfo,
      final @NotNull IScopes scopes,
      final @NotNull SentryAndroidOptions options) {
    // Extract tags
    final Map<String, String> tags = extractTags(startInfo);

    // Create transaction name based on reason
    final String transactionName = "app.start." + getReasonLabel(startInfo.getReason());

    // Create timestamp
    final SentryDate startTimestamp = dateFromMillis(getStartTimestamp(startInfo));

    // Calculate duration (use first frame or fully drawn as end)
    long endTimestamp =
        getFirstFrameTimestamp(startInfo) > 0
            ? getFirstFrameTimestamp(startInfo)
            : getFullyDrawnTimestamp(startInfo);

    final SentryDate endDate =
        endTimestamp > 0 ? dateFromMillis(endTimestamp) : options.getDateProvider().now();

    // Create transaction
    final TransactionContext transactionContext =
        new TransactionContext(transactionName, TransactionNameSource.COMPONENT, "app.start.info");

    final TransactionOptions transactionOptions = new TransactionOptions();
    transactionOptions.setStartTimestamp(startTimestamp);

    final ITransaction transaction =
        scopes.startTransaction(transactionContext, transactionOptions);

    // Add tags
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      transaction.setTag(entry.getKey(), entry.getValue());
    }

    // Create child spans for startup milestones (all start from app launch timestamp)
    attachAppStartMetricData(transaction, startInfo, startTimestamp);

    // Finish transaction
    transaction.finish(SpanStatus.OK, endDate);
  }

  @RequiresApi(api = 35)
  private void attachAppStartMetricData(
      final @NotNull ITransaction transaction,
      final @NotNull android.app.ApplicationStartInfo startInfo,
      final @NotNull SentryDate startTimestamp) {
    final long startMs = getStartTimestamp(startInfo);

    // Span 1: app.start.bind_application (from fork to bind application)
    if (getBindApplicationTimestamp(startInfo) > 0) {
      final io.sentry.ISpan bindSpan =
          transaction.startChild(
              "app.start.bind_application", null, startTimestamp, io.sentry.Instrumenter.SENTRY);
      bindSpan.finish(SpanStatus.OK, dateFromMillis(getBindApplicationTimestamp(startInfo)));
    }

    // Add content provider onCreate spans from AppStartMetrics
    final @NotNull AppStartMetrics appStartMetrics = AppStartMetrics.getInstance();
    final @NotNull List<TimeSpan> contentProviderSpans =
        appStartMetrics.getContentProviderOnCreateTimeSpans();
    for (final TimeSpan cpSpan : contentProviderSpans) {
      if (cpSpan.hasStarted() && cpSpan.hasStopped()) {
        final SentryDate cpStartDate = dateFromMillis(cpSpan.getStartTimestampMs());
        final SentryDate cpEndDate = dateFromMillis(cpSpan.getProjectedStopTimestampMs());

        final io.sentry.ISpan contentProviderSpan =
            transaction.startChild(
                "contentprovider.load",
                cpSpan.getDescription(),
                cpStartDate,
                io.sentry.Instrumenter.SENTRY);
        contentProviderSpan.finish(SpanStatus.OK, cpEndDate);
      }
    }

    // Span 2: app.start.application_oncreate (from fork to Application.onCreate)
    // Use ApplicationStartInfo timestamp if available, enriched with AppStartMetrics description
    final TimeSpan appOnCreateSpan = appStartMetrics.getApplicationOnCreateTimeSpan();
    final String appOnCreateDescription =
        appOnCreateSpan.hasStarted() ? appOnCreateSpan.getDescription() : null;

    if (getApplicationOnCreateTimestamp(startInfo) > 0) {
      // Use precise timestamp from ApplicationStartInfo
      final io.sentry.ISpan onCreateSpan =
          transaction.startChild(
              "app.start.application_oncreate",
              appOnCreateDescription,
              startTimestamp,
              io.sentry.Instrumenter.SENTRY);
      onCreateSpan.finish(
          SpanStatus.OK, dateFromMillis(getApplicationOnCreateTimestamp(startInfo)));
    } else if (appOnCreateSpan.hasStarted() && appOnCreateSpan.hasStopped()) {
      // Fallback to AppStartMetrics timing
      final SentryDate appOnCreateStart = dateFromMillis(appOnCreateSpan.getStartTimestampMs());
      final SentryDate appOnCreateEnd =
          dateFromMillis(appOnCreateSpan.getProjectedStopTimestampMs());

      final io.sentry.ISpan onCreateSpan =
          transaction.startChild(
              "app.start.application_oncreate",
              appOnCreateDescription,
              appOnCreateStart,
              io.sentry.Instrumenter.SENTRY);
      onCreateSpan.finish(SpanStatus.OK, appOnCreateEnd);
    }

    // Span 3: app.start.ttid (from fork to first frame - time to initial display)
    if (getFirstFrameTimestamp(startInfo) > 0) {
      final io.sentry.ISpan ttidSpan =
          transaction.startChild(
              "app.start.ttid", null, startTimestamp, io.sentry.Instrumenter.SENTRY);
      ttidSpan.finish(SpanStatus.OK, dateFromMillis(getFirstFrameTimestamp(startInfo)));
    }

    // Span 4: app.start.ttfd (from fork to fully drawn - time to full display)
    if (getFullyDrawnTimestamp(startInfo) > 0) {
      final io.sentry.ISpan ttfdSpan =
          transaction.startChild(
              "app.start.ttfd", null, startTimestamp, io.sentry.Instrumenter.SENTRY);
      ttfdSpan.finish(SpanStatus.OK, dateFromMillis(getFullyDrawnTimestamp(startInfo)));
    }
  }

  @RequiresApi(api = 35)
  private @NotNull Map<String, String> extractTags(
      final @NotNull android.app.ApplicationStartInfo startInfo) {
    final Map<String, String> tags = new HashMap<>();

    // Add reason
    tags.put("start.reason", getReasonLabel(startInfo.getReason()));

    // Add startup type from ApplicationStartInfo
    tags.put("start.type", getStartupTypeLabel(startInfo.getStartType()));

    // Add launch mode from ApplicationStartInfo
    tags.put("start.launch_mode", getLaunchModeLabel(startInfo.getLaunchMode()));

    // Note: Additional properties like component type, importance, etc. may be added
    // when they become available in future Android API levels

    return tags;
  }

  private @NotNull String getStartupTypeLabel(final int startType) {
    if (Build.VERSION.SDK_INT >= 35) {
      switch (startType) {
        case android.app.ApplicationStartInfo.START_TYPE_COLD:
          return "cold";
        case android.app.ApplicationStartInfo.START_TYPE_WARM:
          return "warm";
        case android.app.ApplicationStartInfo.START_TYPE_HOT:
          return "hot";
        default:
          return "unknown";
      }
    }
    return "unknown";
  }

  private @NotNull String getLaunchModeLabel(final int launchMode) {
    if (Build.VERSION.SDK_INT >= 35) {
      switch (launchMode) {
        case android.app.ApplicationStartInfo.LAUNCH_MODE_STANDARD:
          return "standard";
        case android.app.ApplicationStartInfo.LAUNCH_MODE_SINGLE_TOP:
          return "single_top";
        case android.app.ApplicationStartInfo.LAUNCH_MODE_SINGLE_INSTANCE:
          return "single_instance";
        case android.app.ApplicationStartInfo.LAUNCH_MODE_SINGLE_TASK:
          return "single_task";
        case android.app.ApplicationStartInfo.LAUNCH_MODE_SINGLE_INSTANCE_PER_TASK:
          return "single_instance_per_task";
        default:
          return "unknown";
      }
    }
    return "unknown";
  }

  private @NotNull String getReasonLabel(final int reason) {
    if (Build.VERSION.SDK_INT >= 35) {
      switch (reason) {
        case android.app.ApplicationStartInfo.START_REASON_ALARM:
          return "alarm";
        case android.app.ApplicationStartInfo.START_REASON_BACKUP:
          return "backup";
        case android.app.ApplicationStartInfo.START_REASON_BOOT_COMPLETE:
          return "boot_complete";
        case android.app.ApplicationStartInfo.START_REASON_BROADCAST:
          return "broadcast";
        case android.app.ApplicationStartInfo.START_REASON_CONTENT_PROVIDER:
          return "content_provider";
        case android.app.ApplicationStartInfo.START_REASON_JOB:
          return "job";
        case android.app.ApplicationStartInfo.START_REASON_LAUNCHER:
          return "launcher";
        case android.app.ApplicationStartInfo.START_REASON_OTHER:
          return "other";
        case android.app.ApplicationStartInfo.START_REASON_PUSH:
          return "push";
        case android.app.ApplicationStartInfo.START_REASON_SERVICE:
          return "service";
        case android.app.ApplicationStartInfo.START_REASON_START_ACTIVITY:
          return "start_activity";
        default:
          return "unknown";
      }
    }
    return "unknown";
  }

  // Helper methods to access timestamps from the startupTimestamps map
  @RequiresApi(api = 35)
  private long getStartTimestamp(final @NotNull android.app.ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();
    final Long forkTime = timestamps.get(android.app.ApplicationStartInfo.START_TIMESTAMP_FORK);
    return forkTime != null ? TimeUnit.NANOSECONDS.toMillis(forkTime) : 0;
  }

  @RequiresApi(api = 35)
  private long getBindApplicationTimestamp(
      final @NotNull android.app.ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();
    final Long bindTime =
        timestamps.get(android.app.ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION);
    return bindTime != null ? TimeUnit.NANOSECONDS.toMillis(bindTime) : 0;
  }

  @RequiresApi(api = 35)
  private long getApplicationOnCreateTimestamp(
      final @NotNull android.app.ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();
    final Long onCreateTime =
        timestamps.get(android.app.ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE);
    return onCreateTime != null ? TimeUnit.NANOSECONDS.toMillis(onCreateTime) : 0;
  }

  @RequiresApi(api = 35)
  private long getFirstFrameTimestamp(final @NotNull android.app.ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();
    final Long firstFrameTime =
        timestamps.get(android.app.ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME);
    return firstFrameTime != null ? TimeUnit.NANOSECONDS.toMillis(firstFrameTime) : 0;
  }

  @RequiresApi(api = 35)
  private long getFullyDrawnTimestamp(final @NotNull android.app.ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();
    final Long fullyDrawnTime =
        timestamps.get(android.app.ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN);
    return fullyDrawnTime != null ? TimeUnit.NANOSECONDS.toMillis(fullyDrawnTime) : 0;
  }

  @Override
  public void close() throws IOException {
    try (final ISentryLifecycleToken ignored = startLock.acquire()) {
      isClosed = true;
    }
  }

  /**
   * Creates a SentryDate from milliseconds timestamp. Uses SentryNanotimeDate for compatibility
   * with older Android versions.
   */
  private static @NotNull SentryDate dateFromMillis(final long millis) {
    return new SentryNanotimeDate(new Date(millis), 0);
  }
}
