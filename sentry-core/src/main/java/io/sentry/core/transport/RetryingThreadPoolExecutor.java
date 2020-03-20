package io.sentry.core.transport;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is a thread pool executor enriched for the possibility of retrying the supplied tasks.
 *
 * <p>Note that only {@link Runnable} tasks are retried, a {@link java.util.concurrent.Callable} is
 * not retry-able. Note also that the {@link Future} returned from the {@link #submit(Runnable)} or
 * {@link #submit(Runnable, Object)} methods is NOT generally usable, because it does not work when
 * the task is retried!
 *
 * <p>The {@link Runnable} instances.
 *
 * <p>This class is not public because it is used solely in {@link AsyncConnection}.
 */
final class RetryingThreadPoolExecutor extends ScheduledThreadPoolExecutor {
  private final int maxQueueSize;
  private final AtomicInteger currentlyRunning;

  /**
   * Creates a new instance of the thread pool.
   *
   * @param corePoolSize the minimum number of threads started
   * @param threadFactory the thread factory to construct new threads
   * @param rejectedExecutionHandler specifies what to do with the tasks that cannot be run (e.g.
   *     during the shutdown)
   */
  public RetryingThreadPoolExecutor(
      final int corePoolSize,
      final int maxQueueSize,
      final @NotNull ThreadFactory threadFactory,
      final @NotNull RejectedExecutionHandler rejectedExecutionHandler) {

    super(corePoolSize, threadFactory, rejectedExecutionHandler);
    this.maxQueueSize = maxQueueSize;
    this.currentlyRunning = new AtomicInteger();
  }

  @Override
  public Future<?> submit(final @NotNull Runnable task) {
    if (isSchedulingAllowed()) {
      return super.submit(task);
    } else {
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
      throw new CancellationException();
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
