package io.sentry.config.provider;

import io.sentry.util.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A configuration provider that loads the configuration from the system properties.
 */
public class SystemPropertiesConfigurationProvider implements ConfigurationProvider {
    /**
     * The default prefix of the system properties that should be considered Sentry configuration.
     */
    public static final String DEFAULT_SYSTEM_PROPERTY_PREFIX = "sentry.";

    private static final Logger logger = LoggerFactory.getLogger(SystemPropertiesConfigurationProvider.class);

    private final String prefix;

    /**
     * Constructs a new instance using the {@link #DEFAULT_SYSTEM_PROPERTY_PREFIX}.
     */
    public SystemPropertiesConfigurationProvider() {
        this(DEFAULT_SYSTEM_PROPERTY_PREFIX);
    }

    /**
     * Constructs a new instance that will locate the configuration properties from the system properties having
     * the provided prefix.
     *
     * @param systemPropertyPrefix the prefix of the system properties that should be considered Sentry configuration
     */
    public SystemPropertiesConfigurationProvider(String systemPropertyPrefix) {
        this.prefix = systemPropertyPrefix;
    }

    @Nullable
    @Override
    public String getProperty(String key) {
        String ret = System.getProperty(prefix + key.toLowerCase());

        if (ret != null) {
            logger.debug("Found {}={} in Java System Properties.", key, ret);
        }

        return ret;
    }
}
