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
   * Resolves a list of values for a property given by it's name.
   *
   * @param property - the property name
   * @return the list or null if not found
   */
  @Nullable
  default List<String> getListOrNull(final @NotNull String property) {
    final String value = getProperty(property);
    return value != null ? Arrays.asList(value.split(",")) : null;
  }

  /**
   * Resolves property given by it's name.
   *
   * @param property - the property name
   * @param defaultValue - the default value if property is not set
   * @return property value or the default value if not found.
   */
  @NotNull
  default String getProperty(final @NotNull String property, final @NotNull String defaultValue) {
    final String result = getProperty(property);
    return result != null ? result : defaultValue;
  }

  /**
   * Resolves a boolean property given by it's name.
   *
   * @param property - the property name
   * @return property value or the default value if not found.
   */
  @Nullable
  default Boolean getBooleanProperty(final @NotNull String property) {
    final String result = getProperty(property);
    return result != null ? Boolean.valueOf(result) : null;
  }

  /**
   * Resolves a {@link Double} property given by it's name.
   *
   * @param property - the property name
   * @return property value or the default value if not found.
   */
  @Nullable
  default Double getDoubleProperty(final @NotNull String property) {
    final String prop = getProperty(property);
    Double result = null;
    if (prop != null) {
      try {
        result = Double.valueOf(prop);
      } catch (NumberFormatException e) {
        // ignored
      }
    }
    return result;
  }

  /**
   * Resolves a {@link Long} property given by it's name.
   *
   * @param property - the property name
   * @return property value or the default value if not found.
   */
  @Nullable
  default Long getLongProperty(final @NotNull String property) {
    final String prop = getProperty(property);
    Long result = null;
    if (prop != null) {
      try {
        result = Long.valueOf(prop);
      } catch (NumberFormatException e) {
        // ignored
      }
    }
    return result;
  }
}
