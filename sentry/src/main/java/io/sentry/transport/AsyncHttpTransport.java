package io.sentry.transport;

import io.sentry.DateUtils;
import io.sentry.Hint;
import io.sentry.ILogger;
import io.sentry.RequestDetails;
import io.sentry.SentryDate;
import io.sentry.SentryDateProvider;
import io.sentry.SentryEnvelope;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.UncaughtExceptionHandlerIntegration;
import io.sentry.cache.IEnvelopeCache;
import io.sentry.clientreport.DiscardReason;
import io.sentry.hints.Cached;
import io.sentry.hints.DiskFlushNotification;
import io.sentry.hints.Enqueable;
import io.sentry.hints.Retryable;
import io.sentry.hints.SubmissionResult;
import io.sentry.util.HintUtils;
import io.sentry.util.LogUtils;
import io.sentry.util.Objects;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ITransport} implementation that executes request asynchronously in a blocking manner using
 * {@link java.net.HttpURLConnection}.
 */
public final class AsyncHttpTransport implements ITransport {

  private final @NotNull QueuedThreadPoolExecutor executor;
  private final @NotNull IEnvelopeCache envelopeCache;
  private final @NotNull SentryOptions options;
  private final @NotNull RateLimiter rateLimiter;
  private final @NotNull ITransportGate transportGate;
  private final @NotNull HttpConnection connection;
  private volatile @Nullable Runnable currentRunnable = null;

  public AsyncHttpTransport(
      final @NotNull SentryOptions options,
      final @NotNull RateLimiter rateLimiter,
      final @NotNull ITransportGate transportGate,
      final @NotNull RequestDetails requestDetails) {
    this(
        initExecutor(
            options.getMaxQueueSize(),
            options.getEnvelopeDiskCache(),
            options.getLogger(),
            options.getDateProvider()),
        options,
        rateLimiter,
        transportGate,
        new HttpConnection(options, requestDetails, rateLimiter));
  }

  public AsyncHttpTransport(
      final @NotNull QueuedThreadPoolExecutor executor,
      final @NotNull SentryOptions options,
      final @NotNull RateLimiter rateLimiter,
      final @NotNull ITransportGate transportGate,
      final @NotNull HttpConnection httpConnection) {
    this.executor = Objects.requireNonNull(executor, "executor is required");
    this.envelopeCache =
        Objects.requireNonNull(options.getEnvelopeDiskCache(), "envelopeCache is required");
    this.options = Objects.requireNonNull(options, "options is required");
    this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter is required");
    this.transportGate = Objects.requireNonNull(transportGate, "transportGate is required");
    this.connection = Objects.requireNonNull(httpConnection, "httpConnection is required");
  }

  @Override
  public void send(final @NotNull SentryEnvelope envelope, final @NotNull Hint hint)
      throws IOException {
    // For now no caching on envelopes
    IEnvelopeCache currentEnvelopeCache = envelopeCache;
    boolean cached = false;
    if (HintUtils.hasType(hint, Cached.class)) {
      currentEnvelopeCache = NoOpEnvelopeCache.getInstance();
      cached = true;
      options.getLogger().log(SentryLevel.DEBUG, "Captured Envelope is already cached");
    }

    final SentryEnvelope filteredEnvelope = rateLimiter.filter(envelope, hint);

    if (filteredEnvelope == null) {
      if (cached) {
        envelopeCache.discard(envelope);
      }
    } else {
      SentryEnvelope envelopeThatMayIncludeClientReport;
      if (HintUtils.hasType(
          hint, UncaughtExceptionHandlerIntegration.UncaughtExceptionHint.class)) {
        envelopeThatMayIncludeClientReport =
            options.getClientReportRecorder().attachReportToEnvelope(filteredEnvelope);
      } else {
        envelopeThatMayIncludeClientReport = filteredEnvelope;
      }

      final Future<?> future =
          executor.submit(
              new EnvelopeSender(envelopeThatMayIncludeClientReport, hint, currentEnvelopeCache));

      if (future != null && future.isCancelled()) {
        options
            .getClientReportRecorder()
            .recordLostEnvelope(DiscardReason.QUEUE_OVERFLOW, envelopeThatMayIncludeClientReport);
      } else {
        HintUtils.runIfHasType(
            hint,
            Enqueable.class,
            enqueable -> {
              enqueable.markEnqueued();
              options.getLogger().log(SentryLevel.DEBUG, "Envelope enqueued");
            });
      }
    }
  }

  @Override
  public void flush(long timeoutMillis) {
    executor.waitTillIdle(timeoutMillis);
  }

  private static QueuedThreadPoolExecutor initExecutor(
      final int maxQueueSize,
      final @NotNull IEnvelopeCache envelopeCache,
      final @NotNull ILogger logger,
      final @NotNull SentryDateProvider dateProvider) {

    final RejectedExecutionHandler storeEvents =
        (r, executor) -> {
          if (r instanceof EnvelopeSender) {
            final EnvelopeSender envelopeSender = (EnvelopeSender) r;

            if (!HintUtils.hasType(envelopeSender.hint, Cached.class)) {
              envelopeCache.storeEnvelope(envelopeSender.envelope, envelopeSender.hint);
            }

            markHintWhenSendingFailed(envelopeSender.hint, true);
            logger.log(SentryLevel.WARNING, "Envelope rejected");
          }
        };

    return new QueuedThreadPoolExecutor(
        1, maxQueueSize, new AsyncConnectionThreadFactory(), storeEvents, logger, dateProvider);
  }

  @Override
  public @NotNull RateLimiter getRateLimiter() {
    return rateLimiter;
  }

  @Override
  public boolean isHealthy() {
    boolean anyRateLimitActive = rateLimiter.isAnyRateLimitActive();
    boolean didRejectRecently = executor.didRejectRecently();
    return !anyRateLimitActive && !didRejectRecently;
  }

  @Override
  public void close() throws IOException {
    close(false);
  }

  @Override
  public void close(final boolean isRestarting) throws IOException {
    rateLimiter.close();
    executor.shutdown();
    options.getLogger().log(SentryLevel.DEBUG, "Shutting down");
    try {
      // only stop sending events on a real shutdown, not on a restart
      if (!isRestarting) {
        // We need a small timeout to be able to save to disk any rejected envelope
        long timeout = options.getFlushTimeoutMillis();
        if (!executor.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
          options
              .getLogger()
              .log(
                  SentryLevel.WARNING,
                  "Failed to shutdown the async connection async sender  within "
                      + timeout
                      + " ms. Trying to force it now.");
          executor.shutdownNow();
          if (currentRunnable != null) {
            // We store to disk any envelope that is currently being sent
            executor.getRejectedExecutionHandler().rejectedExecution(currentRunnable, executor);
          }
        }
      }
    } catch (InterruptedException e) {
      // ok, just give up then...
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Thread interrupted while closing the connection.");
      Thread.currentThread().interrupt();
    }
  }

  /**
   * It marks the hints when sending has failed, so it's not necessary to wait the timeout
   *
   * @param hint the Hints
   * @param retry if event should be retried or not
   */
  private static void markHintWhenSendingFailed(final @NotNull Hint hint, final boolean retry) {
    HintUtils.runIfHasType(hint, SubmissionResult.class, result -> result.setResult(false));
    HintUtils.runIfHasType(hint, Retryable.class, retryable -> retryable.setRetry(retry));
  }

  private static final class AsyncConnectionThreadFactory implements ThreadFactory {
    private int cnt;

    @Override
    public @NotNull Thread newThread(final @NotNull Runnable r) {
      final Thread ret = new Thread(r, "SentryAsyncConnection-" + cnt++);
      ret.setDaemon(true);
      return ret;
    }
  }

  private final class EnvelopeSender implements Runnable {
    private final @NotNull SentryEnvelope envelope;
    private final @NotNull Hint hint;
    private final @NotNull IEnvelopeCache envelopeCache;
    private final TransportResult failedResult = TransportResult.error();

    EnvelopeSender(
        final @NotNull SentryEnvelope envelope,
        final @NotNull Hint hint,
        final @NotNull IEnvelopeCache envelopeCache) {
      this.envelope = Objects.requireNonNull(envelope, "Envelope is required.");
      this.hint = hint;
      this.envelopeCache = Objects.requireNonNull(envelopeCache, "EnvelopeCache is required.");
    }

    @Override
    public void run() {
      currentRunnable = this;
      TransportResult result = this.failedResult;
      try {
        result = flush();
        options.getLogger().log(SentryLevel.DEBUG, "Envelope flushed");
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, e, "Envelope submission failed");
        throw e;
      } finally {
        final TransportResult finalResult = result;
        HintUtils.runIfHasType(
            hint,
            SubmissionResult.class,
            (submissionResult) -> {
              options
                  .getLogger()
                  .log(
                      SentryLevel.DEBUG,
                      "Marking envelope submission result: %s",
                      finalResult.isSuccess());
              submissionResult.setResult(finalResult.isSuccess());
            });
        currentRunnable = null;
      }
    }

    private @NotNull TransportResult flush() {
      TransportResult result = this.failedResult;

      envelope.getHeader().setSentAt(null);
      boolean cached = envelopeCache.storeEnvelope(envelope, hint);

      HintUtils.runIfHasType(
          hint,
          DiskFlushNotification.class,
          (diskFlushNotification) -> {
            if (diskFlushNotification.isFlushable(envelope.getHeader().getEventId())) {
              diskFlushNotification.markFlushed();
              options.getLogger().log(SentryLevel.DEBUG, "Disk flush envelope fired");
            } else {
              options
                  .getLogger()
                  .log(
                      SentryLevel.DEBUG,
                      "Not firing envelope flush as there's an ongoing transaction");
            }
          });

      if (transportGate.isConnected()) {
        final SentryEnvelope envelopeWithClientReport =
            options.getClientReportRecorder().attachReportToEnvelope(envelope);
        try {

          @NotNull SentryDate now = options.getDateProvider().now();
          envelopeWithClientReport
              .getHeader()
              .setSentAt(DateUtils.nanosToDate(now.nanoTimestamp()));

          result = connection.send(envelopeWithClientReport);
          if (result.isSuccess()) {
            envelopeCache.discard(envelope);
          } else {
            final String message =
                "The transport failed to send the envelope with response code "
                    + result.getResponseCode();

            options.getLogger().log(SentryLevel.ERROR, message);

            // ignore e.g. 429 as we're not the ones actively dropping
            if (result.getResponseCode() >= 400 && result.getResponseCode() != 429) {
              envelopeCache.discard(envelope);
              options
                  .getClientReportRecorder()
                  .recordLostEnvelope(DiscardReason.NETWORK_ERROR, envelopeWithClientReport);
            }

            throw new IllegalStateException(message);
          }
        } catch (IOException e) {
          // Failure due to IO is allowed to retry the event
          HintUtils.runIfHasType(
              hint,
              Retryable.class,
              (retryable) -> {
                retryable.setRetry(true);
              },
              (hint, clazz) -> {
                if (!cached) {
                  LogUtils.logNotInstanceOf(clazz, hint, options.getLogger());
                  options
                      .getClientReportRecorder()
                      .recordLostEnvelope(DiscardReason.NETWORK_ERROR, envelopeWithClientReport);
                }
              });
          throw new IllegalStateException("Sending the event failed.", e);
        }
      } else {
        // If transportGate is blocking from sending, allowed to retry
        HintUtils.runIfHasType(
            hint,
            Retryable.class,
            (retryable) -> {
              retryable.setRetry(true);
            },
            (hint, clazz) -> {
              if (!cached) {
                LogUtils.logNotInstanceOf(clazz, hint, options.getLogger());
                options
                    .getClientReportRecorder()
                    .recordLostEnvelope(DiscardReason.NETWORK_ERROR, envelope);
              }
            });
      }
      return result;
    }
  }
}
