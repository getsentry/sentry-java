package io.sentry.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
   * Resolves a map for a property given by it's name.
   *
   * @param property - the property name
   * @return the map or empty map if not found
   */
  @NotNull
  Map<String, String> getMap(final @NotNull String property);

  /**
   * Resolves a list of values for a property given by it's name.
   *
   * @param property - the property name
   * @return the list or empty list if not found
   */
  @NotNull
  default List<String> getList(final @NotNull String property) {
    final String value = getProperty(property);
    return value != null ? Arrays.asList(value.split(",")) : Collections.emptyList();
  }

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
