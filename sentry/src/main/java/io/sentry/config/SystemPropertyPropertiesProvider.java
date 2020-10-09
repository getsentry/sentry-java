package io.sentry.config;

import io.sentry.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PropertiesProvider} implementation that resolves properties from Java system properties.
 */
final class SystemPropertyPropertiesProvider implements PropertiesProvider {
  private static final String PREFIX = "sentry";

  @Override
  public @Nullable String getProperty(@NotNull String property) {
    return StringUtils.removeSurrounding(System.getProperty(PREFIX + "." + property), "\"");
  }
}
