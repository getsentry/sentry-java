package io.sentry.config.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.sentry.util.Nullable;

/**
 * Wraps a couple of other configuration providers to act as one, returning the first non-null value for given
 * configuration key, in the iteration order of the wrapped providers.
 */
public class CompoundConfigurationProvider implements MultiConfigurationProvider {
    private final Collection<ConfigurationProvider> providers;

    /**
     * Instantiates the new compound provider by wrapping the provided collection of providers.
     * @param providers the providers to wrap
     */
    public CompoundConfigurationProvider(Collection<ConfigurationProvider> providers) {
        this.providers = providers;
    }

    @Nullable
    @Override
    public String getProperty(String key) {
        for (ConfigurationProvider p : providers) {
            String val = p.getProperty(key);
            if (val != null) {
                return val;
            }
        }

        return null;
    }

    @Override
    public List<String> getAllProperty(String key) {
        List<String> result = new ArrayList<>();
        for (ConfigurationProvider p : providers) {
            String val = p.getProperty(key);
            if (val != null) {
                result.add(val);
            }
        }
        return result;
    }
}
