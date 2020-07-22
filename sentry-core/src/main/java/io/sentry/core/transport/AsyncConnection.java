package io.sentry.core.transport;

import io.sentry.core.ILogger;
import io.sentry.core.SentryEnvelope;
import io.sentry.core.SentryEnvelopeItem;
import io.sentry.core.SentryEvent;
import io.sentry.core.SentryItemType;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.cache.IEnvelopeCache;
import io.sentry.core.cache.IEventCache;
import io.sentry.core.hints.Cached;
import io.sentry.core.hints.DiskFlushNotification;
import io.sentry.core.hints.Retryable;
import io.sentry.core.hints.SessionUpdate;
import io.sentry.core.hints.SubmissionResult;
import io.sentry.core.util.LogUtils;
import io.sentry.core.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** A connection to Sentry that sends the events asynchronously. */
@ApiStatus.Internal
public final class AsyncConnection implements Closeable, Connection {
  private final @NotNull ITransport transport;
  private final @NotNull ITransportGate transportGate;
  private final @NotNull ExecutorService executor;
  private final @NotNull IEventCache eventCache;
  private final @NotNull IEnvelopeCache sessionCache;
  private final @NotNull SentryOptions options;

  public AsyncConnection(
      final ITransport transport,
      final ITransportGate transportGate,
      final IEventCache eventCache,
      final IEnvelopeCache sessionCache,
      final int maxQueueSize,
      final SentryOptions options) {
    this(
        transport,
        transportGate,
        eventCache,
        sessionCache,
        initExecutor(maxQueueSize, eventCache, sessionCache, options.getLogger()),
        options);
  }

  @TestOnly
  AsyncConnection(
      final @NotNull ITransport transport,
      final @NotNull ITransportGate transportGate,
      final @NotNull IEventCache eventCache,
      final @NotNull IEnvelopeCache sessionCache,
      final @NotNull ExecutorService executorService,
      final @NotNull SentryOptions options) {
    this.transport = transport;
    this.transportGate = transportGate;
    this.eventCache = eventCache;
    this.sessionCache = sessionCache;
    this.options = options;
    this.executor = executorService;
  }

  private static QueuedThreadPoolExecutor initExecutor(
      final int maxQueueSize,
      final @NotNull IEventCache eventCache,
      final @NotNull IEnvelopeCache sessionCache,
      final @NotNull ILogger logger) {

    final RejectedExecutionHandler storeEvents =
        (r, executor) -> {
          if (r instanceof EventSender) {
            final EventSender eventSender = (EventSender) r;

            if (!(eventSender.hint instanceof Cached)) {
              eventCache.store(eventSender.event);
            }

            markHintWhenSendingFailed(eventSender.hint, true);
            logger.log(SentryLevel.WARNING, "Event rejected: %s", eventSender.event.getEventId());
          }
          if (r instanceof SessionSender) {
            final SessionSender sessionSender = (SessionSender) r;

            if (!(sessionSender.hint instanceof Cached)) {
              sessionCache.store(sessionSender.envelope, sessionSender.hint);
            }

            markHintWhenSendingFailed(sessionSender.hint, true);
            logger.log(SentryLevel.WARNING, "Envelope rejected");
          }
        };

    return new QueuedThreadPoolExecutor(
        1, maxQueueSize, new AsyncConnectionThreadFactory(), storeEvents, logger);
  }

  /**
   * It marks the hints when sending has failed, so it's not necessary to wait the timeout
   *
   * @param hint the Hint
   * @param retry if event should be retried or not
   */
  private static void markHintWhenSendingFailed(final @Nullable Object hint, final boolean retry) {
    if (hint instanceof SubmissionResult) {
      ((SubmissionResult) hint).setResult(false);
    }
    if (hint instanceof Retryable) {
      ((Retryable) hint).setRetry(retry);
    }
  }

  /**
   * Tries to send the event to the Sentry server.
   *
   * @param event the event to send
   * @throws IOException on error
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public void send(final @NotNull SentryEvent event, final @Nullable Object hint)
      throws IOException {
    IEventCache currentEventCache = eventCache;
    boolean cached = false;
    if (hint instanceof Cached) {
      currentEventCache = NoOpEventCache.getInstance();
      cached = true;
      options.getLogger().log(SentryLevel.DEBUG, "Captured SentryEvent is already cached");
    }

    // no reason to continue
    if (transport.isRetryAfter(SentryItemType.Event.getItemType())) {
      if (cached) {
        eventCache.discard(event);
      }
      markHintWhenSendingFailed(hint, false);
      return;
    }

    executor.submit(new EventSender(event, hint, currentEventCache));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public void send(@NotNull SentryEnvelope envelope, final @Nullable Object hint)
      throws IOException {
    // For now no caching on envelopes
    IEnvelopeCache currentEnvelopeCache = sessionCache;
    boolean cached = false;
    if (hint instanceof Cached) {
      currentEnvelopeCache = NoOpEnvelopeCache.getInstance();
      cached = true;
      options.getLogger().log(SentryLevel.DEBUG, "Captured Envelope is already cached");
    }

    // Optimize for/No allocations if no items are under 429
    List<SentryEnvelopeItem> dropItems = null;
    for (SentryEnvelopeItem item : envelope.getItems()) {
      // using the raw value of the enum to not expose SentryEnvelopeItemType
      if (transport.isRetryAfter(item.getHeader().getType().getItemType())) {
        if (dropItems == null) {
          dropItems = new ArrayList<>();
        }
        dropItems.add(item);
      }
      if (dropItems != null) {
        options
            .getLogger()
            .log(SentryLevel.INFO, "%d items will be dropped due rate limiting.", dropItems.size());
      }
    }

    if (dropItems != null) {
      // Need a new envelope
      List<SentryEnvelopeItem> toSend = new ArrayList<>();
      for (SentryEnvelopeItem item : envelope.getItems()) {
        if (!dropItems.contains(item)) {
          toSend.add(item);
        }
      }

      // no reason to continue
      if (toSend.isEmpty()) {
        if (cached) {
          sessionCache.discard(envelope);
        }
        options.getLogger().log(SentryLevel.INFO, "Envelope discarded due all items rate limited.");

        markHintWhenSendingFailed(hint, false);
        return;
      }

      envelope = new SentryEnvelope(envelope.getHeader(), toSend);
    }

    executor.submit(new SessionSender(envelope, hint, currentEnvelopeCache));
  }

  @Override
  public void close() throws IOException {
    executor.shutdown();
    options.getLogger().log(SentryLevel.DEBUG, "Shutting down");
    try {
      if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Failed to shutdown the async connection async sender within 1 minute. Trying to force it now.");
        executor.shutdownNow();
      }
      transport.close();
    } catch (InterruptedException e) {
      // ok, just give up then...
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Thread interrupted while closing the connection.");
      Thread.currentThread().interrupt();
    }
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

  private final class EventSender implements Runnable {
    private final SentryEvent event;
    private final Object hint;
    private final IEventCache eventCache;
    private final TransportResult failedResult = TransportResult.error(-1);

    EventSender(
        final @NotNull SentryEvent event,
        final @Nullable Object hint,
        final @NotNull IEventCache eventCache) {
      this.event = event;
      this.hint = hint;
      this.eventCache = eventCache;
    }

    @Override
    public void run() {
      TransportResult result = this.failedResult;
      try {
        result = flush();
      } catch (Exception e) {
        options
            .getLogger()
            .log(SentryLevel.ERROR, e, "Event submission failed: %s", event.getEventId());
        throw e;
      } finally {
        if (hint instanceof SubmissionResult) {
          options
              .getLogger()
              .log(SentryLevel.DEBUG, "Marking event submission result: %s", result.isSuccess());
          ((SubmissionResult) hint).setResult(result.isSuccess());
        }
      }
    }

    private @NotNull TransportResult flush() {
      TransportResult result = this.failedResult;
      eventCache.store(event);

      if (hint instanceof DiskFlushNotification) {
        ((DiskFlushNotification) hint).markFlushed();
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Disk flush event fired: %s", event.getEventId());
      }

      if (transportGate.isConnected()) {
        try {
          result = transport.send(event);
          if (result.isSuccess()) {
            eventCache.discard(event);
          } else {
            final String message =
                "The transport failed to send the event with response code "
                    + result.getResponseCode();

            options.getLogger().log(SentryLevel.ERROR, message);

            throw new IllegalStateException(message);
          }
        } catch (IOException e) {
          // Failure due to IO is allowed to retry the event
          if (hint instanceof Retryable) {
            ((Retryable) hint).setRetry(true);
          } else {
            LogUtils.logIfNotRetryable(options.getLogger(), hint);
          }
          throw new IllegalStateException("Sending the event failed.", e);
        }
      } else {
        // If transportGate is blocking from sending, allowed to retry
        if (hint instanceof Retryable) {
          ((Retryable) hint).setRetry(true);
        } else {
          LogUtils.logIfNotRetryable(options.getLogger(), hint);
        }
      }
      return result;
    }
  }

  private final class SessionSender implements Runnable {
    private final @NotNull SentryEnvelope envelope;
    private final @Nullable Object hint;
    private final @NotNull IEnvelopeCache sessionCache;
    private final TransportResult failedResult = TransportResult.error(-1);

    SessionSender(
        final @NotNull SentryEnvelope envelope,
        final @Nullable Object hint,
        final @NotNull IEnvelopeCache sessionCache) {
      this.envelope = Objects.requireNonNull(envelope, "Envelope is required.");
      this.hint = hint;
      this.sessionCache = Objects.requireNonNull(sessionCache, "SessionCache is required.");
    }

    @Override
    public void run() {
      TransportResult result = this.failedResult;
      try {
        result = flush();
        options.getLogger().log(SentryLevel.DEBUG, "Envelope flushed");
      } catch (Exception e) {
        options.getLogger().log(SentryLevel.ERROR, e, "Envelope submission failed");
        throw e;
      } finally {
        if (hint instanceof SubmissionResult) {
          options
              .getLogger()
              .log(SentryLevel.DEBUG, "Marking envelope submission result: %s", result.isSuccess());
          ((SubmissionResult) hint).setResult(result.isSuccess());
        }
      }
    }

    private @NotNull TransportResult flush() {
      TransportResult result = this.failedResult;

      sessionCache.store(envelope, hint);

      // we only flush a session update to the disk, but not to the network
      if (hint instanceof SessionUpdate) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "SessionUpdate event, leaving after event being cached.");
        return TransportResult.success();
      }

      if (transportGate.isConnected()) {
        try {
          result = transport.send(envelope);
          if (result.isSuccess()) {
            sessionCache.discard(envelope);
          } else {
            final String message =
                "The transport failed to send the envelope with response code "
                    + result.getResponseCode();

            options.getLogger().log(SentryLevel.ERROR, message);

            throw new IllegalStateException(message);
          }
        } catch (IOException e) {
          // Failure due to IO is allowed to retry the event
          if (hint instanceof Retryable) {
            ((Retryable) hint).setRetry(true);
          } else {
            LogUtils.logIfNotRetryable(options.getLogger(), hint);
          }
          throw new IllegalStateException("Sending the event failed.", e);
        }
      } else {
        // If transportGate is blocking from sending, allowed to retry
        if (hint instanceof Retryable) {
          ((Retryable) hint).setRetry(true);
        } else {
          LogUtils.logIfNotRetryable(options.getLogger(), hint);
        }
      }
      return result;
    }
  }
}
