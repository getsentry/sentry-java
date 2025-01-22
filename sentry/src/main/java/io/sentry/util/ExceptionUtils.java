package io.sentry.util;

import io.sentry.FilterString;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public static @NotNull boolean isIgnored(
      final @NotNull Set<Class<? extends Throwable>> ignoredExceptionsForType,
      final @Nullable List<FilterString> ignoredExceptions,
      final @NotNull Throwable throwable) {
    if (throwable == null) {
      return false;
    }

    final Class<? extends Throwable> throwableClass = throwable.getClass();
    if (ignoredExceptionsForType.contains(throwableClass)) {
      return true;
    }

    if (ignoredExceptions == null || ignoredExceptions.isEmpty()) {
      return false;
    }
    final String throwableClassName = throwableClass.getCanonicalName();
    if (throwableClassName == null) {
      return false;
    }

    for (final FilterString filter : ignoredExceptions) {
      if (filter.getFilterString().equals(throwableClassName)) {
        return true;
      }
    }

    for (final FilterString filter : ignoredExceptions) {
      if (filter.matches(throwableClassName)) {
        return true;
      }
    }

    return false;
  }
}
