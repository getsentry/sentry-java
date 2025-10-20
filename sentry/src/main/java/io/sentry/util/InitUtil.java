package io.sentry.util;

import io.sentry.IContinuousProfiler;
import io.sentry.IProfileConverter;
import io.sentry.ManifestVersionDetector;
import io.sentry.NoOpContinuousProfiler;
import io.sentry.NoOpProfileConverter;
import io.sentry.NoopVersionDetector;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.profiling.ProfilingServiceLoader;
import java.io.File;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class InitUtil {
  public static boolean shouldInit(
      final @Nullable SentryOptions previousOptions,
      final @NotNull SentryOptions newOptions,
      final boolean isEnabled) {
    if (Platform.isJvm() && newOptions.getVersionDetector() instanceof NoopVersionDetector) {
      newOptions.setVersionDetector(new ManifestVersionDetector(newOptions));
    }
    if (newOptions.getVersionDetector().checkForMixedVersions()) {
      newOptions
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "Not initializing Sentry because mixed SDK versions have been detected.");
      final @NotNull String docsUrl =
          Platform.isAndroid()
              ? "https://docs.sentry.io/platforms/android/troubleshooting/mixed-versions"
              : "https://docs.sentry.io/platforms/java/troubleshooting/mixed-versions";
      throw new IllegalStateException(
          "Sentry SDK has detected a mix of versions. This is not supported and likely leads to crashes. Please always use the same version of all SDK modules (dependencies). See "
              + docsUrl
              + " for more details.");
    }
    if (!isEnabled) {
      return true;
    }

    if (previousOptions == null) {
      return true;
    }

    if (newOptions.isForceInit()) {
      return true;
    }

    return previousOptions.getInitPriority().ordinal() <= newOptions.getInitPriority().ordinal();
  }

  public static IContinuousProfiler initializeProfiler(@NotNull SentryOptions options) {
    IContinuousProfiler continuousProfiler = NoOpContinuousProfiler.getInstance();

    if (options.isContinuousProfilingEnabled()
        && options.getContinuousProfiler() == NoOpContinuousProfiler.getInstance()) {
      try {
        String profilingTracesDirPath = options.getProfilingTracesDirPath();
        if (profilingTracesDirPath == null) {
          File tempDir = new File(System.getProperty("java.io.tmpdir"), "sentry_profiling_traces");
          boolean createDirectorySuccess = tempDir.mkdirs() || tempDir.exists();

          if (!createDirectorySuccess) {
            throw new IllegalArgumentException(
                "Creating a fallback directory for profiling failed in "
                    + tempDir.getAbsolutePath());
          }
          profilingTracesDirPath = tempDir.getAbsolutePath();
          options.setProfilingTracesDirPath(profilingTracesDirPath);
        }

        continuousProfiler =
            ProfilingServiceLoader.loadContinuousProfiler(
                options.getLogger(),
                profilingTracesDirPath,
                options.getProfilingTracesHz(),
                options.getExecutorService());

        if (!(continuousProfiler instanceof NoOpContinuousProfiler)) {
          options.setContinuousProfiler(continuousProfiler);
        }

        return continuousProfiler;

      } catch (Exception e) {
        options
            .getLogger()
            .log(SentryLevel.ERROR, "Failed to create default profiling traces directory", e);
      }
    }
    return continuousProfiler;
  }

  public static IProfileConverter initializeProfileConverter(@NotNull SentryOptions options) {
    IProfileConverter converter = NoOpProfileConverter.getInstance();

    if (options.isContinuousProfilingEnabled()
        && options.getProfilerConverter() instanceof NoOpProfileConverter) {

      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Profile converter is NoOp, attempting to reload with Spring Boot classloader");

      converter = ProfilingServiceLoader.loadProfileConverter();

      options.setProfilerConverter(converter);

      if (!(converter instanceof NoOpProfileConverter)) {
        options
            .getLogger()
            .log(
                SentryLevel.INFO,
                "Successfully loaded profile converter via Spring Boot classloader");
      }
    }
    return converter;
  }
  // TODO: Add initialization of profiler here
}
