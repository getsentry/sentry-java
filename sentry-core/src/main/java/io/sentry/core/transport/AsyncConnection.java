package io.sentry.core.transport;

import static io.sentry.core.transport.RetryingThreadPoolExecutor.HTTP_RETRY_AFTER_DEFAULT_DELAY_MS;

import io.sentry.core.SentryEnvelope;
import io.sentry.core.SentryEvent;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.cache.IEventCache;
import io.sentry.core.cache.ISessionCache;
import io.sentry.core.hints.Cached;
import io.sentry.core.hints.DiskFlushNotification;
import io.sentry.core.hints.RetryableHint;
import io.sentry.core.hints.SubmissionResult;
import java.io.Closeable;
import java.io.IOException;
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
  private final ITransport transport;
  private final ITransportGate transportGate;
  private final ExecutorService executor;
  private final IEventCache eventCache;
  private final ISessionCache sessionCache;
  private final SentryOptions options;

  public AsyncConnection(
      final ITransport transport,
      final ITransportGate transportGate,
      final IEventCache eventCache,
      final ISessionCache sessionCache,
      final int maxQueueSize,
      final SentryOptions options) {
    this(
        transport,
        transportGate,
        eventCache,
        sessionCache,
        initExecutor(maxQueueSize, eventCache, sessionCache),
        options);
  }

  @TestOnly
  AsyncConnection(
      final ITransport transport,
      final ITransportGate transportGate,
      final IEventCache eventCache,
      final ISessionCache sessionCache,
      final ExecutorService executorService,
      final SentryOptions options) {

    this.transport = transport;
    this.transportGate = transportGate;
    this.eventCache = eventCache;
    this.sessionCache = sessionCache;
    this.options = options;
    this.executor = executorService;
  }

  private static RetryingThreadPoolExecutor initExecutor(
      final int maxQueueSize, final IEventCache eventCache, final ISessionCache sessionCache) {

    final RejectedExecutionHandler storeEvents =
        (r, executor) -> {
          if (r instanceof EventSender) {
            eventCache.store(((EventSender) r).event);
          }
          if (r instanceof SessionSender) {
            sessionCache.store(((SessionSender) r).envelope);
          }
        };

    return new RetryingThreadPoolExecutor(
        1, maxQueueSize, new AsyncConnectionThreadFactory(), storeEvents);
  }

  /**
   * Tries to send the event to the Sentry server.
   *
   * @param event the event to send
   * @throws IOException on error
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public void send(final SentryEvent event, final @Nullable Object hint) throws IOException {
    IEventCache currentEventCache = eventCache;
    if (hint instanceof Cached) {
      currentEventCache = NoOpEventCache.getInstance();
    }
    executor.submit(new EventSender(event, hint, currentEventCache));
  }

  // NOTE: This will not fallback to individual /store endpoints. Requires Sentry with Session
  // health feature
  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public void send(@NotNull SentryEnvelope envelope, final @Nullable Object hint)
      throws IOException {
    // For now no caching on envelopes
    ISessionCache currentEventCache = sessionCache;
    if (hint instanceof Cached) {
      currentEventCache = NoOpSessionCache.getInstance();
    }

    // Optimize for/No allocations if no items are under 429
    //    List<SentryEnvelopeItem> dropItems = null;
    //    for (SentryEnvelopeItem item : envelope.getItems()) {
    //      if (item.getHeader() != null && transport.isRetryAfter(item.getHeader().getType())) {
    //        if (dropItems == null) {
    //          dropItems = new ArrayList<>();
    //        }
    //        dropItems.add(item);
    //      }
    //    }
    //
    //    if (dropItems != null) {
    //      // Need a new envelope
    //      List<SentryEnvelopeItem> toSend = new ArrayList<>();
    //      for (SentryEnvelopeItem item : envelope.getItems()) {
    //        if (!dropItems.contains(item)) {
    //          toSend.add(item);
    //        }
    //      }
    //      envelope = new SentryEnvelope(envelope.getHeader(), toSend);
    //    }

    executor.submit(new SessionSender(envelope, hint, currentEventCache));
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
    public Thread newThread(final @NotNull Runnable r) {
      final Thread ret = new Thread(r, "SentryAsyncConnection-" + cnt++);
      ret.setDaemon(true);
      return ret;
    }
  }

  private final class EventSender implements Retryable {
    private final SentryEvent event;
    private final Object hint;
    private final IEventCache eventCache;
    private long suggestedRetryDelay;
    private int responseCode;
    private final TransportResult failedResult =
        TransportResult.error(HTTP_RETRY_AFTER_DEFAULT_DELAY_MS, -1);

    EventSender(final SentryEvent event, final Object hint, final IEventCache eventCache) {
      this.event = event;
      this.hint = hint;
      this.eventCache = eventCache;
    }

    @Override
    public void run() {
      TransportResult result = this.failedResult;
      try {
        result = flush();
        options.getLogger().log(SentryLevel.DEBUG, "Event flushed: %s", event.getEventId());
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

    private TransportResult flush() {
      TransportResult result = this.failedResult;
      eventCache.store(event);
      if (hint instanceof DiskFlushNotification) {
        ((DiskFlushNotification) hint).markFlushed();
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Disk flush event fired: %s", event.getEventId());
      }

      if (transportGate.isSendingAllowed()) {
        try {
          result = transport.send(event);
          if (result.isSuccess()) {
            eventCache.discard(event);
          } else {
            suggestedRetryDelay = result.getRetryMillis();
            responseCode = result.getResponseCode();

            final String message =
                "The transport failed to send the event with response code "
                    + result.getResponseCode()
                    + ". Retrying in "
                    + suggestedRetryDelay
                    + "ms.";

            options.getLogger().log(SentryLevel.ERROR, message);

            throw new IllegalStateException(message);
          }
        } catch (IOException e) {
          // Failure due to IO is allowed to retry the event
          if (hint instanceof RetryableHint) {
            ((RetryableHint) hint).setRetry(true);
          }
          throw new IllegalStateException("Sending the event failed.", e);
        }
      } else {
        // If transportGate is blocking from sending, allowed to retry
        if (hint instanceof RetryableHint) {
          ((RetryableHint) hint).setRetry(true);
        }
      }
      return result;
    }

    @Override
    public long getSuggestedRetryDelayMillis() {
      return suggestedRetryDelay;
    }

    @Override
    public int getResponseCode() {
      return responseCode;
    }
  }

  private final class SessionSender implements Retryable {
    private final SentryEnvelope envelope;
    private final Object hint;
    private final ISessionCache sessionCache;
    private long suggestedRetryDelay;
    private int responseCode;
    private final TransportResult failedResult =
        TransportResult.error(HTTP_RETRY_AFTER_DEFAULT_DELAY_MS, -1);

    SessionSender(
        final SentryEnvelope envelope, final Object hint, final ISessionCache sessionCache) {
      this.envelope = envelope;
      this.hint = hint;
      this.sessionCache = sessionCache;
    }

    @Override
    public void run() {
      TransportResult result = this.failedResult;
      try {
        result = flush();
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Envelope flushed: %s", envelope.getHeader().getEventId());
      } catch (Exception e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                e,
                "Envelope submission failed: %s",
                envelope.getHeader().getEventId());
        throw e;
      } finally {
        // TODO: Now the server will respond (likely in the body, not agreed) for each of the
        // envelope items
        if (hint instanceof SubmissionResult) {
          options
              .getLogger()
              .log(SentryLevel.DEBUG, "Marking envelope submission result: %s", result.isSuccess());
          ((SubmissionResult) hint).setResult(result.isSuccess());
        }
      }
    }

    private TransportResult flush() {
      TransportResult result = this.failedResult;
      // TODO: Do we need special policies for caching envelopes?
      sessionCache.store(envelope, hint);
      if (hint instanceof DiskFlushNotification) {
        ((DiskFlushNotification) hint).markFlushed();
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Disk flush envelope fired: %s",
                envelope.getHeader().getEventId());
      }

      if (transportGate.isSendingAllowed()) {
        try {
          result = transport.send(envelope);
          if (result.isSuccess()) {
            sessionCache.discard(envelope);
          } else {
            suggestedRetryDelay = result.getRetryMillis();
            responseCode = result.getResponseCode();

            final String message =
                "The transport failed to send the event with response code "
                    + result.getResponseCode()
                    + ". Retrying in "
                    + suggestedRetryDelay
                    + "ms.";

            options.getLogger().log(SentryLevel.ERROR, message);

            throw new IllegalStateException(message);
          }
        } catch (IOException e) {
          // Failure due to IO is allowed to retry the event
          if (hint instanceof RetryableHint) {
            ((RetryableHint) hint).setRetry(true);
          }
          throw new IllegalStateException("Sending the event failed.", e);
        }
      } else {
        // If transportGate is blocking from sending, allowed to retry
        if (hint instanceof RetryableHint) {
          ((RetryableHint) hint).setRetry(true);
        }
      }
      return result;
    }

    @Override
    public long getSuggestedRetryDelayMillis() {
      return suggestedRetryDelay;
    }

    @Override
    public int getResponseCode() {
      return responseCode;
    }
  }
}
