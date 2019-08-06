package io.sentry.config.location;

import io.sentry.util.Nullable;

/**
 * Tries to find the location of the Sentry configuration file using the value of the system property called
 * {@code sentry.properties.file}.
 */
public class SystemPropertiesBasedLocator implements ConfigurationResourceLocator {
    private static final String PROPERTY_NAME = "sentry.properties.file";

    @Override
    @Nullable
    public String getConfigurationResourcePath() {
        return System.getProperty(PROPERTY_NAME);
    }
}
