package io.sentry.android.core;

import org.jetbrains.annotations.NotNull;

final class LoadClass implements ILoadClass {
  @Override
  public @NotNull Class<?> loadClass(@NotNull String clazz) throws ClassNotFoundException {
    return Class.forName(clazz);
  }
}
