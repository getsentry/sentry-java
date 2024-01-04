package io.sentry.android.core;

import static io.sentry.Sentry.STARTUP_PROFILING_CONFIG_FILE_NAME;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Build;
import io.sentry.ILogger;
import io.sentry.ITransactionProfiler;
import io.sentry.JsonSerializer;
import io.sentry.SentryExecutorService;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SentryStartupProfilingOptions;
import io.sentry.TracesSamplingDecision;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.instrumentation.file.SentryFileReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class SentryStartupProfilingProvider extends EmptySecureContentProvider {

  private final @NotNull ILogger logger;
  private final @NotNull BuildInfoProvider buildInfoProvider;

  @TestOnly
  SentryStartupProfilingProvider(
      final @NotNull ILogger logger, final @NotNull BuildInfoProvider buildInfoProvider) {
    this.logger = logger;
    this.buildInfoProvider = buildInfoProvider;
  }

  SentryStartupProfilingProvider() {
    logger = new AndroidLogger();
    buildInfoProvider = new BuildInfoProvider(logger);
  }

  @Override
  public boolean onCreate() {
    final @Nullable Context context = getContext();

    if (context == null) {
      logger.log(SentryLevel.FATAL, "App. Context from ContentProvider is null");
      return false;
    }

    // Debug.startMethodTracingSampling() is only available since Lollipop
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP) {
      return false;
    }

    final @NotNull File cacheDir = AndroidOptionsInitializer.getCacheDir(context);
    final @NotNull File configFile = new File(cacheDir, STARTUP_PROFILING_CONFIG_FILE_NAME);

    // No config exists: startup profiling is not enabled
    if (!configFile.exists() || !configFile.canRead()) {
      return false;
    }

    try (final @NotNull Reader fileReader = new SentryFileReader(configFile)) {
      final @Nullable SentryStartupProfilingOptions profilingOptions =
          new JsonSerializer(SentryOptions.empty())
              .deserialize(fileReader, SentryStartupProfilingOptions.class);

      if (profilingOptions == null) {
        logger.log(
            SentryLevel.WARNING,
            "Unable to deserialize the SentryStartupProfilingOptions. Startup profiling will not start.");
        return false;
      }

      if (!profilingOptions.isProfilingEnabled()) {
        logger.log(SentryLevel.INFO, "Profiling is not enabled. Startup profiling will not start.");
        return false;
      }

      final @NotNull TracesSamplingDecision startupSamplingDecision =
          new TracesSamplingDecision(
              profilingOptions.isTraceSampled(),
              profilingOptions.getTraceSampleRate(),
              profilingOptions.isProfileSampled(),
              profilingOptions.getProfileSampleRate());
      // We store any sampling decision, so we can respect it when the first transaction starts
      AppStartMetrics.getInstance().setStartupSamplingDecision(startupSamplingDecision);

      if (!(startupSamplingDecision.getProfileSampled() && startupSamplingDecision.getSampled())) {
        return false;
      }
      logger.log(SentryLevel.DEBUG, "Startup profiling started.");

      final @NotNull ITransactionProfiler startupProfiler =
          new AndroidTransactionProfiler(
              context.getApplicationContext(),
              buildInfoProvider,
              new SentryFrameMetricsCollector(
                  context.getApplicationContext(), logger, buildInfoProvider),
              logger,
              profilingOptions.getProfilingTracesDirPath(),
              profilingOptions.isProfilingEnabled(),
              profilingOptions.getProfilingTracesHz(),
              new SentryExecutorService());
      AppStartMetrics.getInstance().setStartupProfiler(startupProfiler);
      startupProfiler.start();

    } catch (FileNotFoundException e) {
      logger.log(SentryLevel.ERROR, "Startup profiling config file not found. ", e);
      return false;
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, "Error reading startup profiling config file. ", e);
      return false;
    }

    return true;
  }

  @Override
  public void shutdown() {
    final @Nullable ITransactionProfiler startupProfiler =
        AppStartMetrics.getInstance().getStartupProfiler();
    if (startupProfiler != null) {
      startupProfiler.close();
    }
  }

  @Override
  public void attachInfo(@NotNull Context context, @NotNull ProviderInfo info) {
    // applicationId is expected to be prepended. See AndroidManifest.xml
    if (SentryStartupProfilingProvider.class.getName().equals(info.authority)) {
      throw new IllegalStateException("An applicationId is required for startup profiling.");
    }
    super.attachInfo(context, info);
  }

  @Override
  public @Nullable String getType(@NotNull Uri uri) {
    return null;
  }
}
