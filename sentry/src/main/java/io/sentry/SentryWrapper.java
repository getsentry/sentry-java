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
 * that forks the current scope before execution and restores it afterwards. This prevents reused
 * threads (e.g. from thread-pools) from getting an incorrect state.
 */
public final class SentryWrapper {

  /**
   * Helper method to wrap {@link Callable}
   *
   * <p>Forks the current scope before execution and restores it afterwards. This prevents reused
   * threads (e.g. from thread-pools) from getting an incorrect state.
   *
   * @param callable - the {@link Callable} to be wrapped
   * @return the wrapped {@link Callable}
   * @param <U> - the result type of the {@link Callable}
   */
  // TODO [HSM] adapt javadoc
  public static <U> Callable<U> wrapCallable(final @NotNull Callable<U> callable) {
    final IScopes newScopes = Sentry.getCurrentScopes().forkedCurrentScope("wrapCallable");

    return () -> {
      try (ISentryLifecycleToken ignored = newScopes.makeCurrent()) {
        return callable.call();
      }
    };
  }

  public static <U> Callable<U> wrapCallableIsolated(final @NotNull Callable<U> callable) {
    final IScopes newScopes = Sentry.getCurrentScopes().forkedScopes("wrapCallable");

    return () -> {
      try (ISentryLifecycleToken ignored = newScopes.makeCurrent()) {
        return callable.call();
      }
    };
  }

  /**
   * Helper method to wrap {@link Supplier}
   *
   * <p>Forks the current scope before execution and restores it afterwards. This prevents reused
   * threads (e.g. from thread-pools) from getting an incorrect state.
   *
   * @param supplier - the {@link Supplier} to be wrapped
   * @return the wrapped {@link Supplier}
   * @param <U> - the result type of the {@link Supplier}
   */
  public static <U> Supplier<U> wrapSupplier(final @NotNull Supplier<U> supplier) {
    final IScopes newScopes = Sentry.forkedCurrentScope("wrapSupplier");

    return () -> {
      try (ISentryLifecycleToken ignore = newScopes.makeCurrent()) {
        return supplier.get();
      }
    };
  }

  public static <U> Supplier<U> wrapSupplierIsolated(final @NotNull Supplier<U> supplier) {
    final IScopes newScopes = Sentry.forkedScopes("wrapSupplier");

    return () -> {
      try (ISentryLifecycleToken ignore = newScopes.makeCurrent()) {
        return supplier.get();
      }
    };
  }
}
