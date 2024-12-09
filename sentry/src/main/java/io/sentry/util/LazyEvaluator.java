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

  private volatile @Nullable T value = null;
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
   * Executes the evaluator function and caches its result, so that it's called only once, unless
   * resetValue is called.
   *
   * @return The result of the evaluator function.
   */
  public @NotNull T getValue() {
    if (value == null) {
      try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
        if (value == null) {
          value = evaluator.evaluate();
        }
      }
    }

    //noinspection DataFlowIssue
    return value;
  }

  public void setValue(final @Nullable T value) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      this.value = value;
    }
  }

  /**
   * Resets the internal value and forces the evaluator function to be called the next time
   * getValue() is called.
   */
  public void resetValue() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      this.value = null;
    }
  }

  public interface Evaluator<T> {
    @NotNull
    T evaluate();
  }
}
