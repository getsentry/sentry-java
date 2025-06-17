package io.sentry;

import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/**
 * Helper class that provides wrappers around:
 *
 * <ul>
 *   <li>{@link Callable}
 *   <li>{@link Supplier}
 * </ul>
 *
 * that forks the current scope(s) before execution and restores previous state afterwards. Which
 * scope(s) are forked, depends on the method used here. This prevents reused threads (e.g. from
 * thread-pools) from getting an incorrect state.
 */
public final class SentryWrapper {

  /**
   * Helper method to wrap {@link Callable}
   *
   * <p>Forks current and isolation scope before execution and restores previous state afterwards.
   * This prevents reused threads (e.g. from thread-pools) from getting an incorrect state.
   *
   * @param callable - the {@link Callable} to be wrapped
   * @return the wrapped {@link Callable}
   * @param <U> - the result type of the {@link Callable}
   */
  public static <U> Callable<U> wrapCallable(final @NotNull Callable<U> callable) {
    final IScopes newScopes = Sentry.getCurrentScopes().forkedScopes("SentryWrapper.wrapCallable");

    return () -> {
      try (ISentryLifecycleToken ignored = newScopes.makeCurrent()) {
        return callable.call();
      }
    };
  }

  /**
   * Helper method to wrap {@link Supplier}
   *
   * <p>Forks current and isolation scope before execution and restores previous state afterwards.
   * This prevents reused threads (e.g. from thread-pools) from getting an incorrect state.
   *
   * @param supplier - the {@link Supplier} to be wrapped
   * @return the wrapped {@link Supplier}
   * @param <U> - the result type of the {@link Supplier}
   */
  public static <U> Supplier<U> wrapSupplier(final @NotNull Supplier<U> supplier) {
    final IScopes newScopes = Sentry.forkedScopes("SentryWrapper.wrapSupplier");

    return () -> {
      try (ISentryLifecycleToken ignore = newScopes.makeCurrent()) {
        return supplier.get();
      }
    };
  }

  /**
   * Helper method to wrap {@link Runnable}
   *
   * <p>Forks current and isolation scope before execution and restores previous state afterwards.
   * This prevents reused threads (e.g. from thread-pools) from getting an incorrect state.
   *
   * @param runnable - the {@link Runnable} to be wrapped
   * @return the wrapped {@link Runnable}
   */
  public static Runnable wrapRunnable(final @NotNull Runnable runnable) {
    final IScopes newScopes = Sentry.forkedScopes("SentryWrapper.wrapRunnable");

    return () -> {
      try (ISentryLifecycleToken ignore = newScopes.makeCurrent()) {
        runnable.run();
      }
    };
  }
}
