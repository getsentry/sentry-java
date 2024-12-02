package io.sentry.android.core;

import static io.sentry.Sentry.APP_START_PROFILING_CONFIG_FILE_NAME;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import io.sentry.ILogger;
import io.sentry.ITransactionProfiler;
import io.sentry.JsonSerializer;
import io.sentry.SentryAppStartProfilingOptions;
import io.sentry.SentryExecutorService;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.TracesSamplingDecision;
import io.sentry.android.core.internal.util.FirstDrawDoneListener;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.android.core.performance.ActivityLifecycleCallbacksAdapter;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.TimeSpan;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class SentryPerformanceProvider extends EmptySecureContentProvider {

  // static to rely on Class load
  // SystemClock.uptimeMillis() isn't affected by phone provider or clock changes.
  private static final long sdkInitMillis = SystemClock.uptimeMillis();

  private @Nullable Application app;
  private @Nullable Application.ActivityLifecycleCallbacks activityCallback;

  private final @NotNull ILogger logger;
  private final @NotNull BuildInfoProvider buildInfoProvider;

  @TestOnly
  SentryPerformanceProvider(
      final @NotNull ILogger logger, final @NotNull BuildInfoProvider buildInfoProvider) {
    this.logger = logger;
    this.buildInfoProvider = buildInfoProvider;
  }

  public SentryPerformanceProvider() {
    logger = new AndroidLogger();
    buildInfoProvider = new BuildInfoProvider(logger);
  }

  @Override
  public boolean onCreate() {
    final @NotNull AppStartMetrics appStartMetrics = AppStartMetrics.getInstance();
    onAppLaunched(getContext(), appStartMetrics);
    launchAppStartProfiler(appStartMetrics);
    return true;
  }

  @Override
  public void attachInfo(Context context, ProviderInfo info) {
    // applicationId is expected to be prepended. See AndroidManifest.xml
    if (SentryPerformanceProvider.class.getName().equals(info.authority)) {
      throw new IllegalStateException(
          "An applicationId is required to fulfill the manifest placeholder.");
    }
    super.attachInfo(context, info);
  }

  @Nullable
  @Override
  public String getType(@NotNull Uri uri) {
    return null;
  }

  @Override
  public void shutdown() {
    synchronized (AppStartMetrics.getInstance()) {
      final @Nullable ITransactionProfiler appStartProfiler =
          AppStartMetrics.getInstance().getAppStartProfiler();
      if (appStartProfiler != null) {
        appStartProfiler.close();
      }
    }
  }

  private void launchAppStartProfiler(final @NotNull AppStartMetrics appStartMetrics) {
    final @Nullable Context context = getContext();

    if (context == null) {
      logger.log(SentryLevel.FATAL, "App. Context from ContentProvider is null");
      return;
    }

    // Debug.startMethodTracingSampling() is only available since Lollipop
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP) {
      return;
    }

    final @NotNull File cacheDir = AndroidOptionsInitializer.getCacheDir(context);
    final @NotNull File configFile = new File(cacheDir, APP_START_PROFILING_CONFIG_FILE_NAME);

    // No config exists: app start profiling is not enabled
    if (!configFile.exists() || !configFile.canRead()) {
      return;
    }

    try (final @NotNull Reader reader =
            new BufferedReader(new InputStreamReader(new FileInputStream(configFile)))) {
      final @Nullable SentryAppStartProfilingOptions profilingOptions =
          new JsonSerializer(SentryOptions.empty())
              .deserialize(reader, SentryAppStartProfilingOptions.class);

      if (profilingOptions == null) {
        logger.log(
            SentryLevel.WARNING,
            "Unable to deserialize the SentryAppStartProfilingOptions. App start profiling will not start.");
        return;
      }

      if (!profilingOptions.isProfilingEnabled()) {
        logger.log(
            SentryLevel.INFO, "Profiling is not enabled. App start profiling will not start.");
        return;
      }

      final @NotNull TracesSamplingDecision appStartSamplingDecision =
          new TracesSamplingDecision(
              profilingOptions.isTraceSampled(),
              profilingOptions.getTraceSampleRate(),
              profilingOptions.isProfileSampled(),
              profilingOptions.getProfileSampleRate());
      // We store any sampling decision, so we can respect it when the first transaction starts
      appStartMetrics.setAppStartSamplingDecision(appStartSamplingDecision);

      if (!(appStartSamplingDecision.getProfileSampled()
          && appStartSamplingDecision.getSampled())) {
        logger.log(SentryLevel.DEBUG, "App start profiling was not sampled. It will not start.");
        return;
      }
      logger.log(SentryLevel.DEBUG, "App start profiling started.");

      final @NotNull ITransactionProfiler appStartProfiler =
          new AndroidTransactionProfiler(
              context,
              buildInfoProvider,
              new SentryFrameMetricsCollector(context, logger, buildInfoProvider),
              logger,
              profilingOptions.getProfilingTracesDirPath(),
              profilingOptions.isProfilingEnabled(),
              profilingOptions.getProfilingTracesHz(),
              new SentryExecutorService());
      appStartMetrics.setAppStartProfiler(appStartProfiler);
      appStartProfiler.start();

    } catch (FileNotFoundException e) {
      logger.log(SentryLevel.ERROR, "App start profiling config file not found. ", e);
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "Error reading app start profiling config file. ", e);
    }
  }

  @SuppressLint("NewApi")
  private void onAppLaunched(
      final @Nullable Context context, final @NotNull AppStartMetrics appStartMetrics) {

    // sdk-init uses static field init as start time
    final @NotNull TimeSpan sdkInitTimeSpan = appStartMetrics.getSdkInitTimeSpan();
    sdkInitTimeSpan.setStartedAt(sdkInitMillis);

    // performance v2: Uses Process.getStartUptimeMillis()
    // requires API level 24+
    if (buildInfoProvider.getSdkInfoVersion() < android.os.Build.VERSION_CODES.N) {
      return;
    }

    if (context instanceof Application) {
      app = (Application) context;
    }
    if (app == null) {
      return;
    }

    final @NotNull TimeSpan appStartTimespan = appStartMetrics.getAppStartTimeSpan();
    appStartTimespan.setStartedAt(Process.getStartUptimeMillis());
    appStartMetrics.registerApplicationForegroundCheck(app);

    final AtomicBoolean firstDrawDone = new AtomicBoolean(false);

    activityCallback =
        new ActivityLifecycleCallbacksAdapter() {
          @Override
          public void onActivityStarted(@NonNull Activity activity) {
            if (firstDrawDone.get()) {
              return;
            }
            if (activity.getWindow() != null) {
              FirstDrawDoneListener.registerForNextDraw(
                  activity, () -> onAppStartDone(), buildInfoProvider);
            } else {
              new Handler(Looper.getMainLooper()).post(() -> onAppStartDone());
            }
          }
        };

    app.registerActivityLifecycleCallbacks(activityCallback);
  }

  synchronized void onAppStartDone() {
    final @NotNull AppStartMetrics appStartMetrics = AppStartMetrics.getInstance();
    appStartMetrics.getSdkInitTimeSpan().stop();
    appStartMetrics.getAppStartTimeSpan().stop();

    if (app != null) {
      if (activityCallback != null) {
        app.unregisterActivityLifecycleCallbacks(activityCallback);
      }
    }
  }
}
