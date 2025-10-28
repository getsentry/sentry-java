package io.sentry;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** No-op implementation of IDistributionApi. Used when distribution module is not available. */
@ApiStatus.Experimental
public final class NoOpDistributionApi implements IDistributionApi {

  private static final NoOpDistributionApi instance = new NoOpDistributionApi();

  private NoOpDistributionApi() {}

  public static NoOpDistributionApi getInstance() {
    return instance;
  }

  @Override
  public @NotNull UpdateStatus checkForUpdateBlocking() {
    return UpdateStatus.UpToDate.getInstance();
  }

  @Override
  public @NotNull Future<UpdateStatus> checkForUpdate() {
    return new CompletedFuture<>(UpdateStatus.UpToDate.getInstance());
  }

  @Override
  public void downloadUpdate(@NotNull UpdateInfo info) {
    // No-op implementation - do nothing
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  /**
   * A Future implementation that is already completed with a result. This is used instead of
   * CompletableFuture.completedFuture() to maintain compatibility with Android API 21+.
   */
  private static final class CompletedFuture<T> implements Future<T> {
    private final T result;

    CompletedFuture(T result) {
      this.result = result;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
      return false;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public T get() throws ExecutionException {
      return result;
    }

    @Override
    public T get(final long timeout, final @NotNull TimeUnit unit)
        throws ExecutionException, TimeoutException {
      return result;
    }
  }
}
