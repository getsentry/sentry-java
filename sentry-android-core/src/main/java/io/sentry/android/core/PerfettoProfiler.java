package io.sentry.android.core;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ProfilingManager;
import android.os.ProfilingResult;
import androidx.annotation.RequiresApi;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps Android's {@link ProfilingManager} API for Perfetto stack sampling.
 */
@ApiStatus.Internal
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
public class PerfettoProfiler {

  private static final long RESULT_TIMEOUT_SECONDS = 5;

  // Bundle keys matching ProfilingManager constants
  private static final String KEY_DURATION_MS = "KEY_DURATION_MS";
  private static final String KEY_FREQUENCY_HZ = "KEY_FREQUENCY_HZ";

  /** Fixed sampling frequency for Perfetto stack sampling. Not configurable by the developer. */
  private static final int PROFILING_FREQUENCY_HZ = 100;

  private final @NotNull Context context;
  private final @NotNull ILogger logger;
  private @Nullable CancellationSignal cancellationSignal = null;
  private volatile boolean isRunning = false;
  private @Nullable ProfilingResult profilingResult = null;
  private @Nullable CountDownLatch resultLatch = null;

  /**
   * Callback invoked exactly once per {@code requestProfiling} call, either on success (with a file
   * path) or on error (with an error code). Cancelling via {@link CancellationSignal} also triggers
   * this callback.
   */
  private final @NotNull Consumer<ProfilingResult> profilingResultListener;

  public PerfettoProfiler(final @NotNull Context context, final @NotNull ILogger logger) {
    this.context = context;
    this.logger = logger;
    this.profilingResultListener =
        result -> {
          logger.log(
              SentryLevel.DEBUG,
              "Perfetto ProfilingResult received: errorCode=%d, filePath=%s",
              result.getErrorCode(),
              result.getResultFilePath());
          profilingResult = result;
          if (resultLatch != null) {
            resultLatch.countDown();
          }
        };
  }

  public boolean start(final long durationMs) {
    if (isRunning) {
      logger.log(SentryLevel.WARNING, "Perfetto profiling has already started...");
      return false;
    }

    final @Nullable ProfilingManager profilingManager =
        (ProfilingManager) context.getSystemService(Context.PROFILING_SERVICE);
    if (profilingManager == null) {
      logger.log(SentryLevel.WARNING, "ProfilingManager is not available.");
      return false;
    }

    cancellationSignal = new CancellationSignal();
    resultLatch = new CountDownLatch(1);
    profilingResult = null;

    final Bundle params = new Bundle();
    params.putInt(KEY_DURATION_MS, (int) durationMs);
    params.putInt(KEY_FREQUENCY_HZ, PROFILING_FREQUENCY_HZ);

    try {
      profilingManager.requestProfiling(
          ProfilingManager.PROFILING_TYPE_STACK_SAMPLING,
          params,
          "sentry-profiling",
          cancellationSignal,
          Runnable::run,
          profilingResultListener);
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "Failed to request Perfetto profiling.", e);
      cancellationSignal = null;
      resultLatch = null;
      return false;
    }

    isRunning = true;
    return true;
  }

  /**
   * Cancels the current profiling session and blocks until the result is available (up to 5
   * seconds). Returns the trace file on success, or null on error/timeout.
   */
  public @Nullable File endAndCollect() {
    if (!isRunning) {
      logger.log(SentryLevel.WARNING, "Perfetto profiler not running");
      return null;
    }
    isRunning = false;

    if (cancellationSignal != null) {
      cancellationSignal.cancel();
      cancellationSignal = null;
    }

    if (resultLatch != null) {
      try {
        if (!resultLatch.await(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          logger.log(SentryLevel.WARNING, "Timed out waiting for Perfetto profiling result.");
          return null;
        }
      } catch (InterruptedException e) {
        logger.log(SentryLevel.WARNING, "Interrupted while waiting for Perfetto profiling result.");
        Thread.currentThread().interrupt();
        return null;
      }
    }

    if (profilingResult == null) {
      logger.log(SentryLevel.WARNING, "Perfetto profiling result is null.");
      return null;
    }

    final int errorCode = profilingResult.getErrorCode();
    if (errorCode != ProfilingResult.ERROR_NONE) {
      switch (errorCode) {
        case ProfilingResult.ERROR_FAILED_RATE_LIMIT_PROCESS:
        case ProfilingResult.ERROR_FAILED_RATE_LIMIT_SYSTEM:
          logger.log(
              SentryLevel.DEBUG,
              "Perfetto profiling failed: %s."
                  + " To disable during development run:"
                  + " adb shell device_config put profiling_testing rate_limiter.disabled true",
              errorCodeToString(errorCode));
          break;
        default:
          logger.log(
              SentryLevel.WARNING,
              "Perfetto profiling failed with %s (error code %d): %s."
                  + " See https://developer.android.com/reference/android/os/ProfilingResult",
              errorCodeToString(errorCode),
              errorCode,
              profilingResult.getErrorMessage());
          break;
      }
      return null;
    }

    final @Nullable String resultFilePath = profilingResult.getResultFilePath();
    if (resultFilePath == null) {
      logger.log(SentryLevel.WARNING, "Perfetto profiling result file path is null.");
      return null;
    }

    final File traceFile = new File(resultFilePath);
    if (!traceFile.exists() || traceFile.length() == 0) {
      logger.log(SentryLevel.WARNING, "Perfetto trace file does not exist or is empty.");
      return null;
    }

    return traceFile;
  }

  boolean isRunning() {
    return isRunning;
  }

  private static @NotNull String errorCodeToString(final int errorCode) {
    switch (errorCode) {
      case ProfilingResult.ERROR_FAILED_RATE_LIMIT_PROCESS:
        return "ERROR_FAILED_RATE_LIMIT_PROCESS";
      case ProfilingResult.ERROR_FAILED_RATE_LIMIT_SYSTEM:
        return "ERROR_FAILED_RATE_LIMIT_SYSTEM";
      case ProfilingResult.ERROR_FAILED_INVALID_REQUEST:
        return "ERROR_FAILED_INVALID_REQUEST";
      case ProfilingResult.ERROR_FAILED_PROFILING_IN_PROGRESS:
        return "ERROR_FAILED_PROFILING_IN_PROGRESS";
      case ProfilingResult.ERROR_FAILED_POST_PROCESSING:
        return "ERROR_FAILED_POST_PROCESSING";
      case ProfilingResult.ERROR_UNKNOWN:
        return "ERROR_UNKNOWN";
      default:
        return "UNKNOWN_ERROR_CODE";
    }
  }
}
