package io.sentry;

import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Custom {@link ISentryExecutorService} backed by a single daemon worker thread and a {@link
 * PriorityQueue} pre-allocated to {@link #INITIAL_QUEUE_CAPACITY}.
 *
 * <p>Because the backing array is sized at construction time, no array resize ever occurs during
 * normal SDK operation, and {@link #prewarm()} is a no-op.
 */
@ApiStatus.Internal
public final class SentryExecutorService implements ISentryExecutorService {

  /**
   * Pre-allocated initial capacity for the task queue. Sized to comfortably exceed the maximum
   * number of tasks the SDK queues concurrently so the backing array never needs to grow at
   * runtime.
   */
  static final int INITIAL_QUEUE_CAPACITY = 64;

  /**
   * Hard limit on the number of pending tasks. Tasks submitted beyond this limit are silently
   * dropped and a cancelled {@link Future} is returned.
   */
  private static final int MAX_QUEUE_SIZE = 271;

  private final @NotNull PriorityQueue<ScheduledTask<?>> queue;
  private final @NotNull Object lock = new Object();
  private final @NotNull Thread workerThread;
  private final @Nullable SentryOptions options;
  private volatile boolean closed = false;

  public SentryExecutorService() {
    this(null);
  }

  public SentryExecutorService(final @Nullable SentryOptions options) {
    this.options = options;
    this.queue = new PriorityQueue<>(INITIAL_QUEUE_CAPACITY);
    this.workerThread = new Thread(this::loop, "SentryExecutorService");
    this.workerThread.setDaemon(true);
    this.workerThread.start();
  }

  private void loop() {
    while (!closed) {
      ScheduledTask<?> task = null;
      synchronized (lock) {
        while (!closed) {
          final ScheduledTask<?> head = queue.peek();
          if (head == null) {
            try {
              lock.wait();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            }
          } else {
            final long delayNs = head.triggerTimeNs - System.nanoTime();
            if (delayNs <= 0L) {
              task = queue.poll();
              break;
            } else {
              // Sleep until the task is due, or until a new earlier task wakes us.
              final long delayMs = Math.max(1L, delayNs / 1_000_000L);
              try {
                lock.wait(delayMs);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }
            }
          }
        }
      }
      // Execute outside the lock so producers are not blocked during task execution.
      if (task != null && !task.isCancelled()) {
        task.run();
      }
    }
  }

  @Override
  public @NotNull Future<?> submit(final @NotNull Runnable runnable)
      throws RejectedExecutionException {
    return submit(Executors.callable(runnable, (Void) null));
  }

  @Override
  public @NotNull <T> Future<T> submit(final @NotNull Callable<T> callable)
      throws RejectedExecutionException {
    synchronized (lock) {
      if (closed) {
        return new CancelledFuture<>();
      }
      if (queue.size() >= MAX_QUEUE_SIZE) {
        // Purge cancelled tasks before declaring the queue full.
        queue.removeIf(ScheduledTask::isCancelled);
      }
      if (queue.size() >= MAX_QUEUE_SIZE) {
        if (options != null) {
          options
              .getLogger()
              .log(
                  SentryLevel.WARNING,
                  "Task " + callable + " rejected from SentryExecutorService: queue full");
        }
        return new CancelledFuture<>();
      }
      final ScheduledTask<T> task = new ScheduledTask<>(callable, System.nanoTime());
      queue.offer(task);
      lock.notifyAll();
      return task;
    }
  }

  @Override
  public @NotNull Future<?> schedule(final @NotNull Runnable runnable, final long delayMillis)
      throws RejectedExecutionException {
    synchronized (lock) {
      if (closed) {
        return new CancelledFuture<>();
      }
      final ScheduledTask<?> task =
          new ScheduledTask<>(
              Executors.callable(runnable, (Void) null),
              System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMillis));
      queue.offer(task);
      // Wake the worker only if this task is now the earliest — avoids spurious wakeups.
      final ScheduledTask<?> head = queue.peek();
      if (head == task) {
        lock.notifyAll();
      }
      return task;
    }
  }

  @Override
  public void close(final long timeoutMillis) {
    synchronized (lock) {
      if (closed) {
        return;
      }
      closed = true;
      queue.clear();
      lock.notifyAll();
    }
    try {
      workerThread.join(timeoutMillis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (workerThread.isAlive()) {
      workerThread.interrupt();
    }
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  /**
   * No-op. The task queue is pre-allocated at construction time ({@link #INITIAL_QUEUE_CAPACITY}
   * slots), so no warm-up is required.
   *
   * @deprecated Pre-allocation makes this unnecessary. Will be removed in a future release.
   */
  @Override
  @Deprecated
  public void prewarm() {}

  // ---- internals ----

  private static final class ScheduledTask<T> extends FutureTask<T>
      implements Comparable<ScheduledTask<?>> {

    /** Absolute trigger time in nanoseconds ({@link System#nanoTime()}). */
    final long triggerTimeNs;

    ScheduledTask(final @NotNull Callable<T> callable, final long triggerTimeNs) {
      super(callable);
      this.triggerTimeNs = triggerTimeNs;
    }

    @Override
    public int compareTo(final @NotNull ScheduledTask<?> other) {
      return Long.compare(this.triggerTimeNs, other.triggerTimeNs);
    }
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
