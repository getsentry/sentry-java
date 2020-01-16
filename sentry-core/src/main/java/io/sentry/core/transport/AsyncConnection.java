package io.sentry.core.transport;

import io.sentry.core.SentryEvent;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.cache.IEventCache;
import io.sentry.core.hints.Cached;
import io.sentry.core.hints.DiskFlushNotification;
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
  private final SentryOptions options;

  public AsyncConnection(
      ITransport transport,
      ITransportGate transportGate,
      IBackOffIntervalStrategy backOffIntervalStrategy,
      IEventCache eventCache,
      int maxRetries,
      int maxQueueSize,
      SentryOptions options) {
    this(
        transport,
        transportGate,
        eventCache,
        initExecutor(maxRetries, maxQueueSize, backOffIntervalStrategy, eventCache),
        options);
  }

  @TestOnly
  AsyncConnection(
      ITransport transport,
      ITransportGate transportGate,
      IEventCache eventCache,
      ExecutorService executorService,
      SentryOptions options) {
    this.transport = transport;
    this.transportGate = transportGate;
    this.eventCache = eventCache;
    this.options = options;
    this.executor = executorService;
  }

  private static RetryingThreadPoolExecutor initExecutor(
      int maxRetries,
      int maxQueueSize,
      IBackOffIntervalStrategy backOffIntervalStrategy,
      IEventCache eventCache) {

    RejectedExecutionHandler storeEvents =
        (r, executor) -> {
          if (r instanceof EventSender) {
            eventCache.store(((EventSender) r).event);
          }
        };

    return new RetryingThreadPoolExecutor(
        1,
        maxRetries,
        maxQueueSize,
        new AsyncConnectionThreadFactory(),
        backOffIntervalStrategy,
        storeEvents);
  }

  /**
   * Tries to send the event to the Sentry server.
   *
   * @param event the event to send
   * @throws IOException on error
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  // https://errorprone.info/bugpattern/FutureReturnValueIgnored
  @Override
  public void send(SentryEvent event, @Nullable Object hint) throws IOException {
    IEventCache currentEventCache = eventCache;
    if (hint instanceof Cached) {
      currentEventCache = NoOpEventCache.getInstance();
    }
    executor.submit(new EventSender(event, hint, currentEventCache));
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
    public Thread newThread(@NotNull Runnable r) {
      Thread ret = new Thread(r, "SentryAsyncConnection-" + cnt++);
      ret.setDaemon(true);
      return ret;
    }
  }

  private final class EventSender implements Retryable {
    final SentryEvent event;
    private final Object hint;
    private final IEventCache eventCache;
    long suggestedRetryDelay;
    final TransportResult failedResult = TransportResult.error(5000, -1);

    EventSender(SentryEvent event, Object hint, IEventCache eventCache) {
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

            String message =
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
          if (hint instanceof io.sentry.core.hints.Retryable) {
            ((io.sentry.core.hints.Retryable) hint).setRetry(true);
          }
          throw new IllegalStateException("Sending the event failed.", e);
        }
      } else {
        // If transportGate is blocking from sending, allowed to retry
        if (hint instanceof io.sentry.core.hints.Retryable) {
          ((io.sentry.core.hints.Retryable) hint).setRetry(true);
        }
      }
      return result;
    }

    @Override
    public long getSuggestedRetryDelayMillis() {
      return suggestedRetryDelay;
    }
  }
}
