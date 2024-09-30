package io.sentry.util;

import io.sentry.ISentryLifecycleToken;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class that evaluates a function lazily. It means the evaluator function is called only when
 * getValue is called, and it's cached.
 */
@ApiStatus.Internal
public final class LazyEvaluator<T> {
  private @Nullable T value = null;
  private final @NotNull Evaluator<T> evaluator;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  /**
   * Class that evaluates a function lazily. It means the evaluator function is called only when
   * getValue is called, and it's cached.
   *
   * @param evaluator The function to evaluate.
   */
  public LazyEvaluator(final @NotNull Evaluator<T> evaluator) {
    this.evaluator = evaluator;
  }

  /**
   * Executes the evaluator function and caches its result, so that it's called only once.
   *
   * @return The result of the evaluator function.
   */
  public @NotNull T getValue() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (value == null) {
        value = evaluator.evaluate();
      }
      return value;
    }
  }

  public interface Evaluator<T> {
    @NotNull
    T evaluate();
  }
}
