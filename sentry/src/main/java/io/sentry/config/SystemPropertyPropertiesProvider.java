package io.sentry.config;

/**
 * {@link PropertiesProvider} implementation that resolves properties from Java system properties.
 */
final class SystemPropertyPropertiesProvider extends AbstractPropertiesProvider {
  private static final String PREFIX = "sentry.";

  public SystemPropertyPropertiesProvider() {
    super(PREFIX, System.getProperties());
  }
}
