package io.sentry.config;

import io.sentry.util.StringUtils;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PropertiesProvider} implementation that resolves properties from the environment
 * variables.
 */
final class EnvironmentVariablePropertiesProvider implements PropertiesProvider {
  private static final String PREFIX = "SENTRY";

  @Override
  public @Nullable String getProperty(@NotNull String property) {
    return StringUtils.removeSurrounding(
        System.getenv(propertyToEnvironmentVariableName(property)), "\"");
  }

  /**
   * Gets property map based on the property name, where property name is a prefix for the expected
   * environment variable name. For example, when following environment variables are set:
   * SENTRY_TAGS_TAG_1=value1 and SENTRY_TAGS_TAG_2=value2, calling {@code getMap("tags")} returns a
   * map containing pairs "tag1" => "value1" and "tag2" => "value2".
   *
   * @param property the property name
   * @return the map or empty if no matching environment variables found.
   */
  @Override
  public @NotNull Map<String, String> getMap(final @NotNull String property) {
    final String prefix = propertyToEnvironmentVariableName(property) + "_";

    final Map<String, @NotNull String> result = new ConcurrentHashMap<>();
    for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
      final String key = entry.getKey();
      if (key.startsWith(prefix)) {
        final String value = StringUtils.removeSurrounding(entry.getValue(), "\"");
        if (value != null) {
          result.put(key.substring(prefix.length()).toLowerCase(Locale.ROOT), value);
        }
      }
    }
    return result;
  }

  private @NotNull String propertyToEnvironmentVariableName(final @NotNull String property) {
    return PREFIX + "_" + property.replace(".", "_").replace("-", "_").toUpperCase(Locale.ROOT);
  }
}
