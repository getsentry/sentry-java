package io.sentry.config.location;

/**
 * Provides the default configuration file path, {@code sentry.properties}.
 */
public class DefaultLocator implements ConfigurationResourceLocator {
    @Override
    public String getConfigurationResourcePath() {
        return "sentry.properties";
    }
}
