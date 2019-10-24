package io.sentry.spring.autoconfigure;

import io.sentry.config.provider.ConfigurationProvider;

public class SpringBootConfigurationProvider implements ConfigurationProvider {

    private final SentryProperties sentryProperties;

    public SpringBootConfigurationProvider(SentryProperties sentryProperties) {
        this.sentryProperties = sentryProperties;
    }

    @Override
    public String getProperty(String key) {
        return sentryProperties.getOptions() != null ? sentryProperties.getOptions().get(key) : null;
    }

}
