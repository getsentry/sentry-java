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
 * that clones the Hub before execution and restores it afterwards. This prevents reused threads
 * (e.g. from thread-pools) from getting an incorrect state.
 */
public final class SentryWrapper {

  /**
   * Helper method to wrap {@link Callable}
   *
   * <p>Clones the Hub before execution and restores it afterwards. This prevents reused threads
   * (e.g. from thread-pools) from getting an incorrect state.
   *
   * @param callable - the {@link Callable} to be wrapped
   * @return the wrapped {@link Callable}
   * @param <U> - the result type of the {@link Callable}
   */
  @SuppressWarnings("deprecation")
  public static <U> Callable<U> wrapCallable(final @NotNull Callable<U> callable) {
    // TODO replace with forking
    final IScopes newHub = Sentry.getCurrentScopes().clone();

    return () -> {
      final IScopes oldState = Sentry.getCurrentScopes();
      Sentry.setCurrentScopes(newHub);
      try {
        return callable.call();
      } finally {
        Sentry.setCurrentScopes(oldState);
      }
    };
  }

  /**
   * Helper method to wrap {@link Supplier}
   *
   * <p>Clones the Hub before execution and restores it afterwards. This prevents reused threads
   * (e.g. from thread-pools) from getting an incorrect state.
   *
   * @param supplier - the {@link Supplier} to be wrapped
   * @return the wrapped {@link Supplier}
   * @param <U> - the result type of the {@link Supplier}
   */
  @SuppressWarnings("deprecation")
  public static <U> Supplier<U> wrapSupplier(final @NotNull Supplier<U> supplier) {
    // TODO replace with forking
    final IScopes newHub = Sentry.getCurrentScopes().clone();

    return () -> {
      final IScopes oldState = Sentry.getCurrentScopes();
      Sentry.setCurrentScopes(newHub);
      try {
        return supplier.get();
      } finally {
        Sentry.setCurrentScopes(oldState);
      }
    };
  }
}
