package io.sentry.config;

import java.util.Properties;
import org.jetbrains.annotations.NotNull;

/** {@link PropertiesProvider} implementation that delegates to {@link Properties}. */
final class SimplePropertiesProvider extends AbstractPropertiesProvider {

  public SimplePropertiesProvider(final @NotNull Properties properties) {
    super(properties);
  }
}
