package io.sentry.util;

import java.util.Collections;
import java.util.IdentityHashMap;
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
    final Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    visited.add(rootCause);
    Throwable cause = rootCause.getCause();
    while (cause != null && visited.add(cause)) {
      rootCause = cause;
      cause = rootCause.getCause();
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
}
