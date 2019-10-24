package io.sentry.spring.autoconfigure;

import io.sentry.config.provider.ConfigurationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpringBootConfigurationProvider implements ConfigurationProvider {

    private static final Logger logger = LoggerFactory.getLogger(SpringBootConfigurationProvider.class);

    private final SentryProperties sentryProperties;

    public SpringBootConfigurationProvider(SentryProperties sentryProperties) {
        this.sentryProperties = sentryProperties;
    }

    @Override
    public String getProperty(String key) {
        if (sentryProperties.getOptions() == null) {
            return null;
        }

        String ret = sentryProperties.getOptions().get(key);

        if (ret != null) {
            logger.debug("Found {}={} in Spring Boot config.", key, ret);
        }

        return ret;
    }

}
