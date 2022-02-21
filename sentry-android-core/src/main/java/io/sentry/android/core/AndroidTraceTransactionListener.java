package io.sentry.android.core;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.Build;
import android.os.Debug;
import io.sentry.ITransaction;
import io.sentry.ITransactionListener;
import io.sentry.ProfilingTraceData;
import io.sentry.SentryLevel;
import io.sentry.util.Objects;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AndroidTraceTransactionListener implements ITransactionListener {

  /**
   * This appears to correspond to the buffer size of the data part of the file, excluding the key
   * part. Once the buffer is full, new records are ignored, but the resulting trace file will be
   * valid.
   *
   * <p>30 second traces can require a buffer of a few MB. 8MB is the default buffer size for
   * [Debug.startMethodTracingSampling], but 3 should be enough for most cases. We can adjust this
   * in the future if we notice that traces are being truncated in some applications.
   */
  private static final int BUFFER_SIZE_BYTES = 3_000_000;

  private int intervalUs;
  private @Nullable File traceFile = null;
  private @Nullable File traceFilesDir = null;
  private @Nullable Future<?> scheduledFinish = null;
  private volatile @Nullable ITransaction activeTransaction = null;
  private volatile @Nullable ProfilingTraceData lastProfilingData = null;
  private final @NotNull SentryAndroidOptions options;

  public AndroidTraceTransactionListener(final @NotNull SentryAndroidOptions sentryAndroidOptions) {
    this.options = Objects.requireNonNull(sentryAndroidOptions, "SentryAndroidOptions is required");
    final String tracesFilesDirPath = options.getProfilingTracesDirPath();
    if (tracesFilesDirPath == null || tracesFilesDirPath.isEmpty()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Disabling profiling because no profiling traces dir path is defined in options.");
      return;
    }
    long intervalMillis = options.getProfilingTracesIntervalMillis();
    if (intervalMillis <= 0) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Disabling profiling because trace interval is set to %d milliseconds",
              intervalMillis);
      return;
    }
    intervalUs = (int) MILLISECONDS.toMicros(intervalMillis);
    traceFilesDir = new File(tracesFilesDirPath);
  }

  @Override
  public synchronized void onTransactionStart(@NotNull ITransaction transaction) {

    // Debug.startMethodTracingSampling() is only available since Lollipop
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

    // traceFilesDir is null or intervalUs is 0 only if there was a problem in the constructor, but
    // we already logged that
    if (traceFilesDir == null || intervalUs == 0) {
      return;
    }

    // If a transaction is currently being profiled, we ignore this call
    if (activeTransaction != null) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Profiling is already active and was started by transaction %s",
              activeTransaction.getSpanContext().getTraceId().toString());
      return;
    }

    traceFile = new File(traceFilesDir, UUID.randomUUID() + ".trace");

    if (traceFile.exists()) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Trace file already exists: %s", traceFile.getPath());
      return;
    }
    activeTransaction = transaction;

    // We stop the trace after 30 seconds, since such a long trace is very probably a trace
    // that will never end due to an error
    scheduledFinish =
        options
            .getExecutorService()
            .schedule(() -> lastProfilingData = onTransactionFinish(transaction), 30_000);

    Debug.startMethodTracingSampling(traceFile.getPath(), BUFFER_SIZE_BYTES, intervalUs);
  }

  @Override
  public synchronized @Nullable ProfilingTraceData onTransactionFinish(
      @NotNull ITransaction transaction) {

    final ITransaction finalActiveTransaction = activeTransaction;
    // Profiling finished, but we check if we cached last profiling data due to a timeout
    if (finalActiveTransaction == null) {
      ProfilingTraceData profilingData = lastProfilingData;
      // If the cached last profiling data refers to the transaction that started it we return it
      // back, otherwise we would simply lose it
      if (profilingData != null
          && profilingData
              .getTraceId()
              .equals(transaction.getSpanContext().getTraceId().toString())) {
        return profilingData;
      }
      return null;
    }

    if (finalActiveTransaction != transaction) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Transaction %s finished, but profiling was started by transaction %s. Skipping",
              transaction.getSpanContext().getTraceId().toString(),
              finalActiveTransaction.getSpanContext().getTraceId().toString());
      return null;
    }

    Debug.stopMethodTracing();

    lastProfilingData = null;
    activeTransaction = null;

    if (scheduledFinish != null) {
      scheduledFinish.cancel(true);
    }

    if (traceFile == null || !traceFile.exists()) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Trace file %s does not exists",
              traceFile == null ? "null" : traceFile.getPath());
      return null;
    }

    // todo how to retrieve these fields? (version name and code)
    //  buildNumber = Build.VERSION.INCREMENTAL? - osName = "android"?
    return new ProfilingTraceData(
        traceFile,
        transaction,
        Build.VERSION.SDK_INT,
        Build.MANUFACTURER,
        Build.MODEL,
        "buildNumber", // ???
        "android", // ???
        Build.VERSION.RELEASE,
        "versionName", // ???
        "versionCode", // ???
        options.getEnvironment());
  }
}
