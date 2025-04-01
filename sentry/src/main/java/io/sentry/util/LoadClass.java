package io.sentry.util;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** An Adapter for making Class.forName testable */
@Open
public class LoadClass {

  /**
   * Try to load a class via reflection
   *
   * @param clazz the full class name
   * @param logger an instance of ILogger
   * @return a Class&lt;?&gt; if it's available, or null
   */
  public @Nullable Class<?> loadClass(final @NotNull String clazz, final @Nullable ILogger logger) {
    try {
      return Class.forName(clazz);
    } catch (ClassNotFoundException e) {
      if (logger != null && logger.isEnabled(SentryLevel.DEBUG)) {
        logger.log(SentryLevel.DEBUG, "Class not available:" + clazz, e);
      }
    } catch (UnsatisfiedLinkError e) {
      if (logger != null && logger.isEnabled(SentryLevel.ERROR)) {
        logger.log(SentryLevel.ERROR, "Failed to load (UnsatisfiedLinkError) " + clazz, e);
      }
    } catch (Throwable e) {
      if (logger != null && logger.isEnabled(SentryLevel.ERROR)) {
        logger.log(SentryLevel.ERROR, "Failed to initialize " + clazz, e);
      }
    }
    return null;
  }

  public boolean isClassAvailable(final @NotNull String clazz, final @Nullable ILogger logger) {
    return loadClass(clazz, logger) != null;
  }

  public boolean isClassAvailable(
      final @NotNull String clazz, final @Nullable SentryOptions options) {
    return isClassAvailable(clazz, options != null ? options.getLogger() : null);
  }
}
