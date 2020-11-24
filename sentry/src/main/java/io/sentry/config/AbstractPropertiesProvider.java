package io.sentry.config;

import io.sentry.util.Objects;
import io.sentry.util.StringUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for properties provider resolving properties from {@link Properties} based sources.
 */
abstract class AbstractPropertiesProvider implements PropertiesProvider {
  private final @NotNull String prefix;
  private final @NotNull Properties properties;

  protected AbstractPropertiesProvider(
      final @NotNull String prefix, final @NotNull Properties properties) {
    this.prefix = Objects.requireNonNull(prefix, "prefix is required");
    this.properties = Objects.requireNonNull(properties, "properties are required");
  }

  protected AbstractPropertiesProvider(final @NotNull Properties properties) {
    this("", properties);
  }

  @Override
  public @Nullable String getProperty(@NotNull String property) {
    return StringUtils.removeSurrounding(properties.getProperty(prefix + property), "\"");
  }

  /**
   * Gets property map based on the property name, where property name is a prefix for the expected
   * property name. For example, when {@link #prefix} is set to {@code "sentry."} and following
   * properties are set: sentry.tags.tag1=value1 and sentry.tags.tag2=value2, calling {@code
   * getMap("tags")} returns a map containing pairs "tag1" => "value1" and "tag2" => "value2".
   *
   * @param property the property name
   * @return the map or empty if no matching environment variables found.
   */
  @Override
  public @NotNull Map<String, String> getMap(final @NotNull String property) {
    final String prefix = this.prefix + property + ".";

    final Map<String, String> result = new HashMap<>();
    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
        final String key = (String) entry.getKey();
        if (key.startsWith(prefix)) {
          final String value = StringUtils.removeSurrounding((String) entry.getValue(), "\"");
          result.put(key.substring(prefix.length()), value);
        }
      }
    }
    return result;
  }
}
