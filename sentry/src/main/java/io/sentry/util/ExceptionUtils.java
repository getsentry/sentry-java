package io.sentry.util;

import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ExceptionUtils {

  /**
   * Returns exception root cause or the exception itself if there are no causes
   *
   * @param throwable - the throwable
   * @return the root cause
   */
  public static @NotNull Throwable findRootCause(final @NotNull Throwable throwable) {
    Objects.requireNonNull(throwable, "throwable cannot be null");
    Throwable rootCause = throwable;
    while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
      rootCause = rootCause.getCause();
    }
    return rootCause;
  }

  /** Checks if an exception has been ignored. */
  @ApiStatus.Internal
  public static boolean isIgnored(
      final @NotNull Set<Class<? extends Throwable>> ignoredExceptionsForType,
      final @NotNull Throwable throwable) {
    return ignoredExceptionsForType.contains(throwable.getClass());
  }

  /**
   * Handles non-recoverable {@link Throwable}s that should never be swallowed. Rethrows {@link
   * VirtualMachineError} (e.g. OutOfMemoryError/StackOverflowError) and {@link ThreadDeath} as-is.
   * For {@link InterruptedException}, restores the thread's interrupted status instead of
   * rethrowing, since it is a checked exception. All other throwables are left untouched for the
   * caller to handle/log/ignore as before.
   *
   * @param throwable - the throwable to check
   */
  public static void handleFatal(final @NotNull Throwable throwable) {
    // VirtualMachineError covers OutOfMemoryError, StackOverflowError, InternalError, and
    // UnknownError
    if (throwable instanceof VirtualMachineError || throwable instanceof ThreadDeath) {
      throw (Error) throwable;
    }
    if (throwable instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
  }
}
