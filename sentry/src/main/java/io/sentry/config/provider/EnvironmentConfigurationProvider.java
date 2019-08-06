package io.sentry.config.provider;

import io.sentry.util.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tries to find the configuration properties in the environment.
 */
public class EnvironmentConfigurationProvider implements ConfigurationProvider {
    private static final Logger logger = LoggerFactory.getLogger(EnvironmentConfigurationProvider.class);

    @Nullable
    @Override
    public String getProperty(String key) {
        String ret = System.getenv("SENTRY_" + key.replace(".", "_").toUpperCase());

        if (ret != null) {
            logger.debug("Found {}={} in System Environment Variables.", key, ret);
        }

        return ret;
    }
}
