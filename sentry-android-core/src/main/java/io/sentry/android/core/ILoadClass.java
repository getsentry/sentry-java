package io.sentry.android.core;

import org.jetbrains.annotations.NotNull;

/** An Adapter for making Class.forName testable */
interface ILoadClass {

  /**
   * Try to load a class via reflection
   *
   * @param clazz the full class name
   * @return a Class<?>
   * @throws ClassNotFoundException if class is not found
   */
  @NotNull
  Class<?> loadClass(String clazz) throws ClassNotFoundException;
}
