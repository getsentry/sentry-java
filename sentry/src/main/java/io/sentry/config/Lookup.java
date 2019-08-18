package io.sentry.config;

import io.sentry.config.provider.ConfigurationProvider;
import io.sentry.dsn.Dsn;
import io.sentry.util.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle lookup of configuration keys from the various sources.
 *
 * @see io.sentry.SentryOptions for the options and defaults
 */
public final class Lookup {
    private static final Logger logger = LoggerFactory.getLogger(Lookup.class);

    private final ConfigurationProvider highPriorityProvider;
    private final ConfigurationProvider lowPriorityProvider;

    /**
     * Constructs a new Lookup instance.
     *
     * The two configuration providers are used before and after an attempt to load the configuration property from
     * the DSN in the {@link #get(String, Dsn)} method.
     *
     * @param highPriorityProvider the configuration provider that is consulted before the check in DSN
     * @param lowPriorityProvider the configuration provider that is consulted only after the high priority one and
     *                            the DSN
     */
    public Lookup(ConfigurationProvider highPriorityProvider, ConfigurationProvider lowPriorityProvider) {
        this.highPriorityProvider = highPriorityProvider;
        this.lowPriorityProvider = lowPriorityProvider;
    }

    /**
     * Attempt to lookup a configuration key, without checking any DSN options.
     *
     * @param key name of configuration key, e.g. "dsn"
     * @return value of configuration key, if found, otherwise null
     */
    @Nullable
    public String get(String key) {
        return get(key, null);
    }

    /**
     * Attempt to lookup a configuration key using either some internal means or from the DSN.
     *
     * @param key name of configuration key, e.g. "dsn"
     * @param dsn an optional DSN to retrieve options from
     * @return value of configuration key, if found, otherwise null
     */
    @Nullable
    public String get(String key, Dsn dsn) {
        String val = highPriorityProvider.getProperty(key);

        if (val == null && dsn != null) {
            val = dsn.getOptions().get(key);
            if (val != null) {
                logger.debug("Found {}={} in DSN.", key, val);
            }
        }

        if (val == null) {
            val = lowPriorityProvider.getProperty(key);
        }

        return val == null ? null : val.trim();
    }
}
