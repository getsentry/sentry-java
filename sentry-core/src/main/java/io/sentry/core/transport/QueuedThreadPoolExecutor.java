package io.sentry.core.transport;

import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is a thread pool executor enriched for the possibility of queueing (with max queue size) the
 * supplied tasks.
 *
 * <p>The {@link Runnable} instances.
 *
 * <p>This class is not public because it is used solely in {@link AsyncConnection}.
 */
final class QueuedThreadPoolExecutor extends ThreadPoolExecutor {
  private final int maxQueueSize;
  private final AtomicInteger currentlyRunning;
  private final @NotNull ILogger logger;

  /**
   * Creates a new instance of the thread pool.
   *
   * @param corePoolSize the minimum number of threads started
   * @param threadFactory the thread factory to construct new threads
   * @param rejectedExecutionHandler specifies what to do with the tasks that cannot be run (e.g.
   *     during the shutdown)
   */
  public QueuedThreadPoolExecutor(
      final int corePoolSize,
      final int maxQueueSize,
      final @NotNull ThreadFactory threadFactory,
      final @NotNull RejectedExecutionHandler rejectedExecutionHandler,
      final @NotNull ILogger logger) {
    // similar to Executors.newSingleThreadExecutor, but with a max queue size control
    super(
        corePoolSize,
        corePoolSize,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        threadFactory,
        rejectedExecutionHandler);
    this.maxQueueSize = maxQueueSize;
    this.currentlyRunning = new AtomicInteger();
    this.logger = logger;
  }

  @Override
  public Future<?> submit(final @NotNull Runnable task) {
    if (isSchedulingAllowed()) {
      return super.submit(task);
    } else {
      // if the thread pool is full, we don't cache it
      logger.log(SentryLevel.WARNING, "Submit cancelled");
      return new CancelledFuture<>();
    }
  }

  @Override
  protected void beforeExecute(final @NotNull Thread t, final @NotNull Runnable r) {
    try {
      super.beforeExecute(t, r);
    } finally {
      currentlyRunning.incrementAndGet();
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  protected void afterExecute(final @NotNull Runnable r, final @Nullable Throwable t) {
    try {
      super.afterExecute(r, t);
    } finally {
      currentlyRunning.decrementAndGet();
    }
  }

  private boolean isSchedulingAllowed() {
    return getQueue().size() + currentlyRunning.get() < maxQueueSize;
  }

  private static final class CancelledFuture<T> implements Future<T> {
    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
      return true;
    }

    @Override
    public boolean isCancelled() {
      return true;
    }

    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public T get() {
      throw new CancellationException();
    }

    @Override
    public T get(final long timeout, final @NotNull TimeUnit unit) {
      throw new CancellationException();
    }
  }
}
