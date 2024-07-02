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
  public static <U> Callable<U> wrapCallable(final @NotNull Callable<U> callable) {
    final IHub newHub = Sentry.getCurrentHub().clone();

    return () -> {
      final IHub oldState = Sentry.getCurrentHub();
      Sentry.setCurrentHub(newHub);
      try {
        return callable.call();
      } finally {
        Sentry.setCurrentHub(oldState);
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
  public static <U> Supplier<U> wrapSupplier(final @NotNull Supplier<U> supplier) {

    final IHub newHub = Sentry.getCurrentHub().clone();

    return () -> {
      final IHub oldState = Sentry.getCurrentHub();
      Sentry.setCurrentHub(newHub);
      try {
        return supplier.get();
      } finally {
        Sentry.setCurrentHub(oldState);
      }
    };
  }
}
