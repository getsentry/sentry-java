package io.sentry.config.provider;

import io.sentry.util.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tries to find the configuration properties in the environment.
 */
public class EnvironmentConfigurationProvider implements ConfigurationProvider {
    /**
     * The default prefix of the environment variables holding Sentry configuration.
     */
    public static final String DEFAULT_ENV_VAR_PREFIX = "SENTRY_";

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentConfigurationProvider.class);

    private final String prefix;

    /**
     * Creates a new instance with environment variables assumed to have the {@link #DEFAULT_ENV_VAR_PREFIX}.
     */
    public EnvironmentConfigurationProvider() {
        this(DEFAULT_ENV_VAR_PREFIX);
    }

    /**
     * Creates a new instance that will look for env variables having the provided prefix.
     *
     * @param envVarPrefix the prefix of the environment variables
     */
    public EnvironmentConfigurationProvider(String envVarPrefix) {
        this.prefix = envVarPrefix;
    }

    @Nullable
    @Override
    public String getProperty(String key) {
        String ret = System.getenv(prefix + key.replace(".", "_").toUpperCase());

        if (ret != null) {
            logger.debug("Found {}={} in System Environment Variables.", key, ret);
        }

        return ret;
    }
}
