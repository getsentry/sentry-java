package io.sentry.util;

import org.jetbrains.annotations.NotNull;

/** An Adapter for making Class.forName testable */
public final class LoadClass {

  /**
   * Try to load a class via reflection
   *
   * @param clazz the full class name
   * @return a Class<?>
   * @throws ClassNotFoundException if class is not found
   */
  public @NotNull Class<?> loadClass(@NotNull String clazz) throws ClassNotFoundException {
    return Class.forName(clazz);
  }
}
