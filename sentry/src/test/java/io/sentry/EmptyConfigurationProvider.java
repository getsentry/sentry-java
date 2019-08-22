package io.sentry;

import io.sentry.config.provider.ConfigurationProvider;
import io.sentry.util.Nullable;

public class EmptyConfigurationProvider implements ConfigurationProvider {
    @Nullable
    @Override
    public String getProperty(String key) {
        return null;
    }
}
