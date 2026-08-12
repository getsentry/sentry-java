package io.sentry;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * The {@link Future} an {@link ISentryExecutorService} hands back for a task it will never run.
 * Callers can tell such a task apart from an accepted one through {@link #isCancelled()}, and are
 * spared blocking in {@link #get()} on a result that is never coming.
 */
final class CancelledFuture<T> implements Future<T> {
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
