package io.sentry.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Helpers for safely calling methods from {@code compileOnly} dependencies that may not exist at
 * runtime.
 *
 * <p>When a dependency is {@code compileOnly}, the app supplies the actual version. That version
 * may predate the API we compiled against, which means it may not have methods we call from our
 * source code if the API has added methods over time. Calling a missing method throws a {@link
 * LinkageError} (e.g., {@link NoSuchMethodError}, {@link AbstractMethodError}, etc.). This class
 * lets us centralize the try/catch-with-fallback pattern.
 *
 * <p>This is not for {@code implementation} dependencies with transitive version conflicts on the
 * classpath; use local {@code try/catch} handling for those cases instead.
 */
@ApiStatus.Internal
public final class CompileOnlyCompat {

  /**
   * Functional interface for a call that returns a non-null value and may throw a {@link
   * LinkageError}.
   *
   * <p>Use it when calling from Kotlin:
   *
   * <pre>{@code
   * CompileOnlyCall { delegate.hasConnectionPool }.ifAbsent(false)
   * }</pre>
   */
  @FunctionalInterface
  public interface CompileOnlyCall<T> {

    @NotNull
    T call();

    /**
     * Invokes this callable and returns its result. If the call throws a {@link LinkageError},
     * returns {@code fallback} instead.
     */
    default @NotNull T ifAbsent(final @NotNull T fallback) {
      return run(this, error -> fallback);
    }

    /**
     * Invokes this callable and returns its result. If the call throws a {@link LinkageError},
     * invokes {@code fallback} and returns its result instead.
     */
    default @NotNull T ifAbsent(final @NotNull Fallback<T> fallback) {
      return run(this, fallback);
    }
  }

  /** Fallback that receives the caught {@link LinkageError} and returns a non-null value. */
  @FunctionalInterface
  public interface Fallback<T> {
    @NotNull
    T call(@NotNull LinkageError error);
  }

  private CompileOnlyCompat() {}

  private static <T> @NotNull T run(
      final @NotNull CompileOnlyCall<T> call, final @NotNull Fallback<T> fallback) {
    try {
      return call.call();
    } catch (LinkageError e) {
      return fallback.call(e);
    }
  }
}
