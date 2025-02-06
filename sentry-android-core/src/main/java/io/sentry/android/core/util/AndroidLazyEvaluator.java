package io.sentry.android.core.util;

import android.content.Context;
import io.sentry.util.LazyEvaluator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class that evaluates a function lazily. It means the evaluator function is called only when
 * getValue is called, and it's cached. Same as {@link LazyEvaluator} but accepts Context as an
 * argument for {@link AndroidLazyEvaluator#getValue}.
 */
@ApiStatus.Internal
public final class AndroidLazyEvaluator<T> {

  private volatile @Nullable T value = null;
  private final @NotNull AndroidEvaluator<T> evaluator;

  /**
   * Class that evaluates a function lazily. It means the evaluator function is called only when
   * getValue is called, and it's cached.
   *
   * @param evaluator The function to evaluate.
   */
  public AndroidLazyEvaluator(final @NotNull AndroidEvaluator<T> evaluator) {
    this.evaluator = evaluator;
  }

  /**
   * Executes the evaluator function and caches its result, so that it's called only once, unless
   * resetValue is called.
   *
   * @return The result of the evaluator function.
   */
  public @Nullable T getValue(final @NotNull Context context) {
    if (value == null) {
      synchronized (this) {
        if (value == null) {
          value = evaluator.evaluate(context);
        }
      }
    }

    return value;
  }

  public void setValue(final @Nullable T value) {
    synchronized (this) {
      this.value = value;
    }
  }

  /**
   * Resets the internal value and forces the evaluator function to be called the next time
   * getValue() is called.
   */
  public void resetValue() {
    synchronized (this) {
      this.value = null;
    }
  }

  public interface AndroidEvaluator<T> {
    @Nullable
    T evaluate(@NotNull Context context);
  }
}
