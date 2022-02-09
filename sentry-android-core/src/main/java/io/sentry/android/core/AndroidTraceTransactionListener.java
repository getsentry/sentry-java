package io.sentry.android.core;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.Build;
import android.os.Debug;
import io.sentry.HubAdapter;
import io.sentry.IHub;
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

  private final @NotNull IHub hub;
  private @Nullable File traceFile = null;
  private @Nullable File traceFilesDir = null;
  private @Nullable Future<?> scheduledFinish = null;
  private volatile @Nullable ITransaction activeTransaction = null;
  private volatile @Nullable ProfilingTraceData lastProfilingData = null;

  public AndroidTraceTransactionListener() {
    this(HubAdapter.getInstance());
  }

  public AndroidTraceTransactionListener(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    final String tracesFilesDirPath = hub.getOptions().getProfilingTracesDirPath();
    if (tracesFilesDirPath == null || tracesFilesDirPath.isEmpty()) {
      hub.getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "No profiling traces dir path is defined in options.");
      return;
    }
    traceFilesDir = new File(tracesFilesDirPath);
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public synchronized void onTransactionStart(@NotNull ITransaction transaction) {

    // Debug.startMethodTracingSampling() is only available since Lollipop
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

    // If a transaction is currently being profiled, we ignore this call
    if (activeTransaction != null) {
      return;
    }

    traceFile =
        traceFilesDir == null ? null : new File(traceFilesDir, UUID.randomUUID() + ".trace");

    if (traceFile == null) {
      hub.getOptions().getLogger().log(SentryLevel.DEBUG, "Could not create a trace file");
      return;
    }

    if (traceFile.exists()) {
      hub.getOptions()
          .getLogger()
          .log(SentryLevel.DEBUG, "Trace file already exists: %s", traceFile.getPath());
      return;
    }

    long intervalMillis = hub.getOptions().getProfilingTracesIntervalMillis();
    if (intervalMillis <= 0) {
      hub.getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Profiling trace interval is set to %d milliseconds",
              intervalMillis);
      return;
    }
    activeTransaction = transaction;

    // We stop the trace after 30 seconds, since such a long trace is very probably a trace
    // that will never end due to an error
    scheduledFinish =
        hub.getOptions()
            .getExecutorService()
            .schedule(() -> lastProfilingData = onTransactionFinish(transaction), 30_000);

    int intervalUs = (int) MILLISECONDS.toMicros(intervalMillis);
    Debug.startMethodTracingSampling(traceFile.getPath(), BUFFER_SIZE_BYTES, intervalUs);
  }

  @Override
  public synchronized @Nullable ProfilingTraceData onTransactionFinish(
      @NotNull ITransaction transaction) {

    // Profiling finished, but we check if we cached last profiling data due to a timeout
    if (activeTransaction == null) {
      ProfilingTraceData profilingData = lastProfilingData;
      // If the cached last profiling data refers to the transaction that started it we return it
      // back, otherwise we would simply lose it
      if (profilingData != null
          && profilingData.getTraceId().equals(transaction.getSpanContext().getTraceId())) {
        return profilingData;
      }
      return null;
    }

    if (activeTransaction != transaction) {
      return null;
    }

    Debug.stopMethodTracing();

    lastProfilingData = null;

    if (scheduledFinish != null) {
      scheduledFinish.cancel(true);
    }

    if (traceFile == null || !traceFile.exists()) {
      hub.getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Trace file %s does not exists",
              traceFile == null ? "null" : traceFile.getPath());
      return null;
    }

    return new ProfilingTraceData(
        traceFile,
        transaction.getSpanContext().getTraceId(),
        transaction.getSpanContext().getSpanId());
  }
}
