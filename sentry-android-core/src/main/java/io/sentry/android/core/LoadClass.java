package io.sentry.android.core;

import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** An Adapter for making Class.forName testable */
public final class LoadClass {

  /**
   * Try to load a class via reflection
   *
   * @param clazz the full class name
   * @param options an instance of SentryOptions
   * @return a Class<?> if it's available, or null
   */
  public @Nullable Class<?> loadClass(
      final @NotNull String clazz, final @Nullable SentryOptions options) {
    try {
      return Class.forName(clazz);
    } catch (ClassNotFoundException e) {
      if (options != null) {
        options.getLogger().log(SentryLevel.WARNING, "Failed to load class " + clazz, e);
      }
    } catch (UnsatisfiedLinkError e) {
      if (options != null) {
        options
            .getLogger()
            .log(SentryLevel.ERROR, "Failed to load (UnsatisfiedLinkError) " + clazz, e);
      }
    } catch (Throwable e) {
      if (options != null) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to initialize " + clazz, e);
      }
    }
    return null;
  }

  public boolean isClassAvailable(
      final @NotNull String clazz, final @Nullable SentryOptions options) {
    return loadClass(clazz, options) != null;
  }
}
