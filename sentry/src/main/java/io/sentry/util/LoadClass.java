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
   * Loads and initializes a class via reflection. Use this when you intend to actually use the
   * class (e.g. instantiate it or invoke its methods). The returned class is fully initialized, so
   * its static initializers run. To merely check whether a class is on the classpath, use {@link
   * #isClassAvailable} instead, which avoids running those initializers.
   *
   * @param clazz the full class name
   * @param logger an instance of ILogger
   * @return a Class&lt;?&gt; if it's available, or null
   */
  public @Nullable Class<?> loadClass(final @NotNull String clazz, final @Nullable ILogger logger) {
    return loadClass(clazz, logger, true);
  }

  private @Nullable Class<?> loadClass(
      final @NotNull String clazz, final @Nullable ILogger logger, final boolean initialize) {
    try {
      return Class.forName(clazz, initialize, LoadClass.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      if (logger != null) {
        logger.log(SentryLevel.INFO, "Class not available: " + clazz);
      }
    } catch (UnsatisfiedLinkError e) {
      if (logger != null) {
        logger.log(SentryLevel.ERROR, "Failed to load (UnsatisfiedLinkError) " + clazz, e);
      }
    } catch (Throwable e) {
      if (logger != null) {
        logger.log(SentryLevel.ERROR, "Failed to initialize " + clazz, e);
      }
    }
    return null;
  }

  /**
   * Probes whether a class is on the classpath without initializing it. Use this for availability
   * checks (e.g. deciding whether to register an integration); the class is not initialized, so its
   * static initializers do not run until something actually uses it. This keeps SDK init cheap by
   * not triggering unrelated initializers. If you need to use the class, use {@link #loadClass}
   * instead.
   *
   * @param clazz the full class name
   * @param logger an instance of ILogger
   * @return true if the class is on the classpath
   */
  public boolean isClassAvailable(final @NotNull String clazz, final @Nullable ILogger logger) {
    return loadClass(clazz, logger, false) != null;
  }

  public boolean isClassAvailable(
      final @NotNull String clazz, final @Nullable SentryOptions options) {
    return isClassAvailable(clazz, options != null ? options.getLogger() : null);
  }

  /**
   * Like {@link #isClassAvailable}, but defers the (non-initializing) availability check until the
   * result is first read. Use this when the check itself should not run during SDK init but only
   * later, on first access.
   *
   * @param clazz the full class name
   * @param logger an instance of ILogger
   * @return a lazily-evaluated availability check
   */
  public LazyEvaluator<Boolean> isClassAvailableLazy(
      final @NotNull String clazz, final @Nullable ILogger logger) {
    return new LazyEvaluator<>(() -> isClassAvailable(clazz, logger));
  }

  public LazyEvaluator<Boolean> isClassAvailableLazy(
      final @NotNull String clazz, final @Nullable SentryOptions options) {
    return new LazyEvaluator<>(() -> isClassAvailable(clazz, options));
  }
}
