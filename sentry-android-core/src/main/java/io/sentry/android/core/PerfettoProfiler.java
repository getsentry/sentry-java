package io.sentry.android.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ProfilingManager;
import android.os.ProfilingResult;
import androidx.annotation.RequiresApi;
import io.sentry.ILogger;
import io.sentry.ISentryExecutorService;
import io.sentry.SentryLevel;
import java.io.File;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps Android's {@link ProfilingManager} API for a single Perfetto stack-sampling session.
 *
 * <p>Each instance is single-use: call {@link #start} once, then {@link #endAndCollect} once. For a
 * new profiling session, create a new instance.
 */
@ApiStatus.Internal
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
public class PerfettoProfiler {

  // Bundle keys matching ProfilingManager constants
  private static final String KEY_DURATION_MS = "KEY_DURATION_MS";
  private static final String KEY_FREQUENCY_HZ = "KEY_FREQUENCY_HZ";

  /** Fixed sampling frequency for Perfetto stack sampling. Not configurable by the developer. */
  private static final int PROFILING_FREQUENCY_HZ = 100;

  private static final long RESULT_TIMEOUT_MS = 5000;

  private final @NotNull ILogger logger;
  private final @NotNull ISentryExecutorService executorService;
  private final @Nullable ProfilingManager profilingManager;
  private final @NotNull CancellationSignal cancellationSignal = new CancellationSignal();

  private final @NotNull Object profilingResultLock = new Object();
  private volatile @Nullable ProfilingResult profilingResult = null;

  private @Nullable Consumer<@Nullable File> resultListener = null;
  private volatile boolean started = false;

  @SuppressLint("WrongConstant")
  public PerfettoProfiler(
      final @NotNull Context context,
      final @NotNull ILogger logger,
      final @NotNull ISentryExecutorService executorService) {
    this(
        logger,
        executorService,
        (ProfilingManager) context.getSystemService(Context.PROFILING_SERVICE));
  }

  PerfettoProfiler(
      final @NotNull ILogger logger,
      final @NotNull ISentryExecutorService executorService,
      final @Nullable ProfilingManager profilingManager) {
    this.logger = logger;
    this.executorService = executorService;
    this.profilingManager = profilingManager;
  }

  public boolean start(final long durationMs) {
    if (started) {
      logger.log(SentryLevel.WARNING, "PerfettoProfiler was already started.");
      return false;
    }
    started = true;

    if (profilingManager == null) {
      logger.log(SentryLevel.WARNING, "ProfilingManager is not available.");
      return false;
    }

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
          this::onProfilingResult);
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "Failed to request Profiling.", e);
      return false;
    }

    return true;
  }

  /**
   * Cancels the current profiling session. The listener is called with the trace file (or null on
   * error) once the OS delivers the result. The listener may be called synchronously if the result
   * has already arrived, or asynchronously on an OS-managed thread otherwise.
   */
  public void endAndCollect(final @NotNull Consumer<@Nullable File> listener) {
    if (!started) {
      logger.log(SentryLevel.WARNING, "PerfettoProfiler was never started");
      listener.accept(null);
      return;
    }

    cancellationSignal.cancel();

    synchronized (profilingResultLock) {
      final @Nullable ProfilingResult result = profilingResult;
      if (result != null) {
        listener.accept(processResult(result));
        return;
      }
      resultListener = listener;
    }

    try {
      executorService.schedule(
          () -> {
            synchronized (profilingResultLock) {
              if (resultListener != null) {
                logger.log(SentryLevel.WARNING, "Timed out waiting for Perfetto profiling result.");
                resultListener.accept(null);
                resultListener = null;
              }
            }
          },
          RESULT_TIMEOUT_MS);
    } catch (RejectedExecutionException e) {
      logger.log(SentryLevel.DEBUG, "Failed to schedule profiling result timeout.", e);
    }
  }

  private void onProfilingResult(final @NotNull ProfilingResult result) {
    logger.log(
        SentryLevel.DEBUG,
        "Perfetto ProfilingResult received: errorCode=%d, filePath=%s",
        result.getErrorCode(),
        result.getResultFilePath());

    synchronized (profilingResultLock) {
      profilingResult = result;
      if (resultListener != null) {
        resultListener.accept(processResult(result));
        resultListener = null;
      }
    }
  }

  private @Nullable File processResult(final @NotNull ProfilingResult result) {
    final int errorCode = result.getErrorCode();
    if (errorCode != ProfilingResult.ERROR_NONE) {
      switch (errorCode) {
        case ProfilingResult.ERROR_FAILED_RATE_LIMIT_PROCESS:
        case ProfilingResult.ERROR_FAILED_RATE_LIMIT_SYSTEM:
          logger.log(
              SentryLevel.INFO,
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
              result.getErrorMessage());
          break;
      }
      return null;
    }

    final @Nullable String resultFilePath = result.getResultFilePath();
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
