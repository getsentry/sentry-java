package io.sentry.android.core;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.Build;
import android.os.Debug;
import io.sentry.ITransaction;
import io.sentry.ITransactionListener;
import io.sentry.Sentry;
import io.sentry.SentryEnvelope;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.util.FileUtils;
import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import io.sentry.util.SentryExecutors;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
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

  private @Nullable File traceFile = null;
  private @Nullable File traceFilesDir = null;
  private boolean startedMethodTracing = false;
  private @Nullable ITransaction activeTransaction = null;

  private @NotNull final SentryOptions options;

  public AndroidTraceTransactionListener(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "SentryOptions is required");
    final String tracesFilesDirPath = this.options.getProfilingTracesDirPath();
    if (tracesFilesDirPath == null || tracesFilesDirPath.isEmpty()) {
      this.options
          .getLogger()
          .log(SentryLevel.ERROR, "No profiling traces dir path is defined in options.");
      return;
    }

    traceFilesDir = new File(tracesFilesDirPath);
    // Method trace files are normally deleted at the end of traces, but if that fails
    // for some reason we try to clear any old files here.
    FileUtils.deleteRecursively(traceFilesDir);
    traceFilesDir.mkdirs();
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public synchronized void onTransactionStart(ITransaction transaction) {

    // Debug.startMethodTracingSampling() is only available since Lollipop
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

    // Let's be sure to end any running trace
    if (activeTransaction != null) onTransactionEnd(activeTransaction);

    traceFile = FileUtils.resolve(traceFilesDir, "sentry-" + UUID.randomUUID() + ".trace");

    if (traceFile == null) {
      options.getLogger().log(SentryLevel.DEBUG, "Could not create a trace file");
      return;
    }

    if (traceFile.exists()) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Trace file already exists: %s", traceFile.getPath());
      return;
    }

    long intervalMs = options.getProfilingTracesIntervalMs();
    if (intervalMs <= 0) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Profiling trace interval is set to %d milliseconds", intervalMs);
      return;
    }

    // We stop the trace after 30 seconds, since such a long trace is very probably a trace
    // that will never end due to an error
    SentryExecutors.tracingExecutor.schedule(
        () -> onTransactionEnd(transaction), 30_000, MILLISECONDS);

    startedMethodTracing = true;
    int intervalUs = (int) MILLISECONDS.toMicros(intervalMs);
    activeTransaction = transaction;
    Debug.startMethodTracingSampling(traceFile.getPath(), BUFFER_SIZE_BYTES, intervalUs);
  }

  @Override
  public synchronized void onTransactionEnd(ITransaction transaction) {
    // In case a previous timeout tries to end a newer transaction we simply ignore it
    if (transaction != activeTransaction) return;

    if (startedMethodTracing) {
      startedMethodTracing = false;

      Debug.stopMethodTracing();

      if (traceFile == null || !traceFile.exists()) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Trace file %s does not exists",
                traceFile == null ? "null" : traceFile.getPath());
        return;
      }

      // todo should I use transaction.getEventId() instead of new SentryId()?
      //  Or should I add the transaction id as an header to the envelope?
      //  Or should I simply ignore the transaction entirely (wouldn't make any sense)?
      //  And how to check if a trace is from a startup?
      try {
        SentryEnvelope envelope =
            SentryEnvelope.from(
                new SentryId(),
                traceFile.getPath(),
                traceFile.getName(),
                options.getSdkVersion(),
                options.getMaxTraceFileSize(),
                true);
        Sentry.getCurrentHub().captureEnvelope(envelope);
      } catch (IOException e) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to capture session.", e);
        return;
      }
    }

    if (traceFile != null) traceFile.delete();

    activeTransaction = null;
    traceFile = null;
  }
}
