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
   * Rethrows non-recoverable {@link Throwable}s that should never be swallowed: {@link
   * VirtualMachineError} (e.g. OutOfMemoryError/StackOverflowError), {@link ThreadDeath} and {@link
   * LinkageError} are rethrown as-is. For {@link InterruptedException}, the thread's interrupted
   * status is restored instead of rethrowing, since it is a checked exception. All other throwables
   * are left untouched for the caller to handle/log/ignore as before.
   *
   * <p>Note on {@link LinkageError}: its subclasses (e.g. {@link NoClassDefFoundError}, {@link
   * NoSuchMethodError}, {@link AbstractMethodError}, {@link UnsatisfiedLinkError}) are also the
   * expected runtime signal for a missing or version-mismatched optional dependency, which is a
   * normal condition for integrations built against {@code compileOnly} dependencies. Call sites
   * that probe for such a dependency must catch the relevant {@link LinkageError} subclass locally
   * <em>before</em> delegating to this method — see {@code SentrySQLiteDriver.hasConnectionPool}
   * and {@link LoadClass} for examples.
   *
   * @param throwable - the throwable to check
   */
  public static void rethrowIfFatal(final @NotNull Throwable throwable) {
    // VirtualMachineError covers OutOfMemoryError, StackOverflowError, InternalError, and
    // UnknownError
    if (throwable instanceof VirtualMachineError
        || throwable instanceof ThreadDeath
        || throwable instanceof LinkageError) {
      throw (Error) throwable;
    }
    if (throwable instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
  }
}
