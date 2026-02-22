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
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.cache.AndroidEnvelopeCache;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.IntegrationUtils;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ApplicationStartInfoIntegration implements Integration, Closeable {

  private final @NotNull Context context;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull AutoClosableReentrantLock startLock = new AutoClosableReentrantLock();
  private final @NotNull List<IApplicationStartInfoProcessor> processors =
      new CopyOnWriteArrayList<>();
  private boolean isClosed;

  public ApplicationStartInfoIntegration(
      final @NotNull Context context, final @NotNull BuildInfoProvider buildInfoProvider) {
    this.context = ContextUtils.getApplicationContext(context);
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
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

    if (options.isEnableApplicationStartInfoTracing()) {
      addProcessor(new ApplicationStartInfoTracingProcessor(options));
    }
    if (options.isEnableApplicationStartInfoMetrics()) {
      addProcessor(new ApplicationStartInfoMetricsProcessor(options));
    }

    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                try (final ISentryLifecycleToken ignored = startLock.acquire()) {
                  if (!isClosed) {
                    final ActivityManager activityManager =
                        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

                    if (activityManager == null) {
                      options
                          .getLogger()
                          .log(SentryLevel.ERROR, "Failed to retrieve ActivityManager.");
                      return;
                    }

                    processHistoricalAppStarts(activityManager, scopes, options);
                    registerAppStartListener(activityManager, scopes, options);
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
      final @NotNull ActivityManager activityManager,
      final @NotNull IScopes scopes,
      final @NotNull SentryAndroidOptions options) {
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
  private void processHistoricalAppStarts(
      final @NotNull ActivityManager activityManager,
      final @NotNull IScopes scopes,
      final @NotNull SentryAndroidOptions options) {

    try {
      final List<ApplicationStartInfo> historicalStarts =
          activityManager.getHistoricalProcessStartReasons(0);

      if (historicalStarts.isEmpty()) {
        options.getLogger().log(SentryLevel.DEBUG, "No historical app starts available.");
        return;
      }

      final Long lastReportedTimestamp = AndroidEnvelopeCache.lastReportedAppStart(options);

      for (ApplicationStartInfo startInfo : historicalStarts) {
        if (lastReportedTimestamp != null) {
          final Long forkTime =
              startInfo.getStartupTimestamps().get(ApplicationStartInfo.START_TIMESTAMP_FORK);
          if (forkTime != null && forkTime <= lastReportedTimestamp) {
            options
                .getLogger()
                .log(
                    SentryLevel.DEBUG,
                    "Skipping already reported historical app start: %d",
                    forkTime);
            continue;
          }
        }

        if (!isCompleted(startInfo)) {
          options
              .getLogger()
              .log(SentryLevel.DEBUG, "Historical app start not completed, skipping");
          continue;
        }

        processAppStartInfo(startInfo, scopes, options);
      }
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to process historical ApplicationStartInfo.", e);
    }
  }

  @RequiresApi(api = 35)
  private void onApplicationStartInfoAvailable(
      final @NotNull ApplicationStartInfo startInfo,
      final @NotNull IScopes scopes,
      final @NotNull SentryAndroidOptions options) {

    if (!isCompleted(startInfo)) {
      options.getLogger().log(SentryLevel.DEBUG, "App start not completed (no TTID), skipping");
      return;
    }

    processAppStartInfo(startInfo, scopes, options);
  }

  @RequiresApi(api = 35)
  private boolean isCompleted(final @NotNull ApplicationStartInfo startInfo) {
    final Map<Integer, Long> timestamps = startInfo.getStartupTimestamps();
    final Long ttid = timestamps.get(ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME);
    return ttid != null && ttid > 0;
  }

  @RequiresApi(api = 35)
  private void processAppStartInfo(
      final @NotNull ApplicationStartInfo startInfo,
      final @NotNull IScopes scopes,
      final @NotNull SentryAndroidOptions options) {

    final Map<String, String> tags = extractTags(startInfo);

    final long currentUnixMs = System.currentTimeMillis();
    final long currentRealtimeMs = SystemClock.elapsedRealtime();
    final long unixTimeOffsetMs = currentUnixMs - currentRealtimeMs;
    AppStartMetrics.getInstance().setApplicationStartInfo(startInfo, tags, unixTimeOffsetMs);

    for (IApplicationStartInfoProcessor processor : processors) {
      try {
        processor.process(startInfo, tags, scopes);
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, "Processor failed", e);
      }
    }

    final Long forkTime =
        startInfo.getStartupTimestamps().get(ApplicationStartInfo.START_TIMESTAMP_FORK);
    if (forkTime != null) {
      AndroidEnvelopeCache.storeAppStartTimestamp(options, forkTime);
    }
  }

  private void addProcessor(final @NotNull IApplicationStartInfoProcessor processor) {
    processors.add(processor);
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

  @Override
  public void close() throws IOException {
    try (final ISentryLifecycleToken ignored = startLock.acquire()) {
      isClosed = true;
    }
  }
}
