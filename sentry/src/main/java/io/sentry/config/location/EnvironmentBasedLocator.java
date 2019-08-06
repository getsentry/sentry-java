package io.sentry.config.location;

import io.sentry.util.Nullable;

/**
 * Tries to find the location of the Sentry configuration file in the environment variable called
 * {@code SENTRY_PROPERTIES_FILE}.
 */
public class EnvironmentBasedLocator implements ConfigurationResourceLocator {
    private static final String VARIABLE_NAME = "SENTRY_PROPERTIES_FILE";

    @Override
    @Nullable
    public String getConfigurationResourcePath() {
        return System.getenv(VARIABLE_NAME);
    }
}
