package io.sentry.android.core;

import io.sentry.ILogger;
import io.sentry.SentryOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An Adapter for making Class.forName testable
 *
 * @deprecated please use {@link io.sentry.util.LoadClass} instead.
 */
@Deprecated
public final class LoadClass extends io.sentry.util.LoadClass {

  private final io.sentry.util.LoadClass delegate;

  public LoadClass() {
    delegate = new io.sentry.util.LoadClass();
  }

  /**
   * Try to load a class via reflection
   *
   * @param clazz the full class name
   * @param logger an instance of ILogger
   * @return a Class<?> if it's available, or null
   */
  public @Nullable Class<?> loadClass(final @NotNull String clazz, final @Nullable ILogger logger) {
    return delegate.loadClass(clazz, logger);
  }

  public boolean isClassAvailable(final @NotNull String clazz, final @Nullable ILogger logger) {
    return delegate.isClassAvailable(clazz, logger);
  }

  public boolean isClassAvailable(
      final @NotNull String clazz, final @Nullable SentryOptions options) {
    return delegate.isClassAvailable(clazz, options);
  }
}
