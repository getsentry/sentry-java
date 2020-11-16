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

  /**
   * Resolves property given by it's name.
   *
   * @param property - the property name
   * @param defaultValue - the default value if property is not set
   * @return property value or the default value if not found.
   */
  @NotNull
  default String getProperty(@NotNull String property, @NotNull String defaultValue) {
    final String result = getProperty(property);
    return result != null ? result : defaultValue;
  }
}
