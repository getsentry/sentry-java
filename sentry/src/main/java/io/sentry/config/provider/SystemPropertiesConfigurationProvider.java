package io.sentry.config.provider;

import io.sentry.util.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A configuration provider that loads the configuration from the system properties.
 */
public class SystemPropertiesConfigurationProvider implements ConfigurationProvider {
    private static final Logger logger = LoggerFactory.getLogger(SystemPropertiesConfigurationProvider.class);

    @Nullable
    @Override
    public String getProperty(String key) {
        String ret = System.getProperty("sentry." + key.toLowerCase());

        if (ret != null) {
            logger.debug("Found {}={} in Java System Properties.", key, ret);
        }

        return ret;
    }
}
