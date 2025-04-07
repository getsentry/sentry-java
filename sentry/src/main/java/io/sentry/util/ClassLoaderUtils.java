package io.sentry.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ClassLoaderUtils {

  public static @NotNull ClassLoader classLoaderOrDefault(final @Nullable ClassLoader classLoader) {
    // bootstrap classloader is represented as null, so using system classloader instead
    if (classLoader == null) {
      // try thread context classloader
      final @Nullable ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
      if (contextClassLoader != null) {
        return contextClassLoader;
      }
      // fallback to system classloader
      return ClassLoader.getSystemClassLoader();
    } else {
      return classLoader;
    }
  }
}
