package io.sentry.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PropertiesProvider {
  /**
   * Resolves property given by it's name.
   *
   * @param property - the property name
   * @return property value or {@code null} if not found.
   */
  @Nullable
  String getProperty(@NotNull String property);
}
