package io.sentry.core.transport;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/**
 * This is a thread pool executor enriched for the possibility of retrying the supplied tasks.
 *
 * <p>Note that only {@link Runnable} tasks are retried, a {@link java.util.concurrent.Callable} is
 * not retry-able. Note also that the {@link Future} returned from the {@link #submit(Runnable)} or
 * {@link #submit(Runnable, Object)} methods is NOT generally usable, because it does not work when
 * the task is retried!
 *
 * <p>The {@link Runnable} instances may in addition implement the {@link Retryable} interface to
 * suggest the required delay before the next attempt.
 *
 * <p>This class is not public because it is used solely in {@link AsyncConnection}.
 */
final class RetryingThreadPoolExecutor extends ScheduledThreadPoolExecutor {
  private final int maxQueueSize;
  private final AtomicInteger currentlyRunning;

  private static final int HTTP_TOO_MANY_REQUESTS = 429;
  static final long HTTP_RETRY_AFTER_DEFAULT_DELAY_MS = 60000; // default 60s

  private final AtomicBoolean retryAfter = new AtomicBoolean(false);

  private final Timer timer = new Timer(true);
  private TimerTask timerTaskRetryAfter;

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
      final ThreadFactory threadFactory,
      final RejectedExecutionHandler rejectedExecutionHandler) {

    super(corePoolSize, threadFactory, rejectedExecutionHandler);
    this.maxQueueSize = maxQueueSize;
    this.currentlyRunning = new AtomicInteger();
  }

  /**
   * A special overload to submit {@link Retryable} tasks.
   *
   * @param task the task to execute
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  public void submit(final Retryable task) {
    if (isSchedulingAllowed()) {
      super.submit(task);
    }
  }

  @Override
  public Future<?> submit(final Runnable task) {
    if (isSchedulingAllowed()) {
      return super.submit(task);
    } else {
      return new CancelledFuture<>();
    }
  }

  @Override
  public <T> Future<T> submit(final Runnable task, final T result) {
    if (isSchedulingAllowed()) {
      return super.submit(task, result);
    } else {
      return new CancelledFuture<>();
    }
  }

  @Override
  public <T> Future<T> submit(final Callable<T> task) {
    if (isSchedulingAllowed()) {
      return super.submit(task);
    } else {
      return new CancelledFuture<>();
    }
  }

  @Override
  protected <V> RunnableScheduledFuture<V> decorateTask(
      Runnable runnable, final RunnableScheduledFuture<V> task) {

    if (runnable instanceof NextAttempt) {
      runnable = ((NextAttempt) runnable).runnable;
    }

    return new AttemptedRunnable<>(task, runnable);
  }

  @Override
  protected void beforeExecute(final Thread t, final Runnable r) {
    super.beforeExecute(t, r);
    currentlyRunning.incrementAndGet();
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  protected void afterExecute(final Runnable r, Throwable t) {
    try {
      super.afterExecute(r, t);

      if (!(r instanceof AttemptedRunnable)) {
        return;
      }

      final AttemptedRunnable<?> ar = (AttemptedRunnable) r;

      // taken verbatim from the javadoc of the method in ThreadPoolExecutor - this makes sure we
      // capture the exceptions from the tasks
      if (t == null) {
        try {
          ar.get();
        } catch (CancellationException ce) {
          t = ce;
        } catch (ExecutionException ee) {
          t = ee.getCause();
        } catch (InterruptedException ie) {
          // ok, we're interrupted - mark the thread again and give up
          Thread.currentThread().interrupt();
          return;
        }
      }

      if (t != null) {
        long delayMillis = -1;
        int responseCode = -1;
        if (ar.suppliedAction instanceof Retryable) {
          delayMillis = ((Retryable) ar.suppliedAction).getSuggestedRetryDelayMillis();
          responseCode = ((Retryable) ar.suppliedAction).getResponseCode();
        }

        if (responseCode == HTTP_TOO_MANY_REQUESTS) {
          // just a check for sanity
          if (delayMillis <= 0) {
            delayMillis = HTTP_RETRY_AFTER_DEFAULT_DELAY_MS;
          }

          scheduleRetryAfterDelay(delayMillis);
          // TODO: This needs to be re-worked now.
          // TODO: Either we need to ThreadPoolExecutors, or we need to support different 429's per
          // item type (within envelopes)
          getQueue().clear();
        }
      }
    } finally {
      currentlyRunning.decrementAndGet();
    }
  }

  private void scheduleRetryAfterDelay(final long delayMillis) {
    if (!retryAfter.getAndSet(true)) {
      if (timerTaskRetryAfter != null) {
        timerTaskRetryAfter.cancel();
      }
      timerTaskRetryAfter =
          new TimerTask() {
            @Override
            public void run() {
              retryAfter.set(false);
            }
          };

      timer.schedule(timerTaskRetryAfter, delayMillis);
    }
  }

  private boolean isSchedulingAllowed() {
    return getQueue().size() + currentlyRunning.get() < maxQueueSize && !retryAfter.get();
  }

  private static final class NextAttempt implements Runnable {
    private final Runnable runnable;

    private NextAttempt(final Runnable runnable) {
      this.runnable = runnable;
    }

    @Override
    public void run() {
      runnable.run();
    }
  }

  private static final class AttemptedRunnable<V> implements RunnableScheduledFuture<V> {
    private final RunnableScheduledFuture<?> task;
    private final Runnable suppliedAction;

    AttemptedRunnable(final RunnableScheduledFuture<?> task, final Runnable suppliedAction) {
      this.task = task;
      this.suppliedAction = suppliedAction;
    }

    @Override
    public boolean isPeriodic() {
      return task.isPeriodic();
    }

    @Override
    public long getDelay(final @NotNull TimeUnit unit) {
      return task.getDelay(unit);
    }

    @Override
    public int compareTo(final @NotNull Delayed o) {
      return task.compareTo(o);
    }

    @Override
    public void run() {
      task.run();
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
      return task.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
      return task.isCancelled();
    }

    @Override
    public boolean isDone() {
      return task.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
      task.get();
      return null;
    }

    @Override
    public V get(final long timeout, final @NotNull TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      task.get(timeout, unit);
      return null;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      AttemptedRunnable<?> that = (AttemptedRunnable<?>) o;
      return task.equals(that.task);
    }

    @Override
    public int hashCode() {
      return task.hashCode();
    }

    @Override
    public String toString() {
      return task.toString();
    }
  }

  private static final class CancelledFuture<T> implements Future<T> {
    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
      return false;
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
