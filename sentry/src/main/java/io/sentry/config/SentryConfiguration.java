package io.sentry.config;

import io.sentry.dsn.Dsn;
import io.sentry.util.Nullable;

/**
 * Implementations provide configuration values used by Sentry.
 */
public interface SentryConfiguration {

    /**
     * Attempt to lookup a configuration key, without checking any DSN options.
     *
     * @param key name of configuration key, e.g. "dsn"
     * @return value of configuration key, if found, otherwise null
     */
    @Nullable
    String get(String key);

    /**
     * Attempt to lookup a configuration key using either some internal means or from the DSN.
     *
     * @param key name of configuration key, e.g. "dsn"
     * @param dsn an optional DSN to retrieve options from
     * @return value of configuration key, if found, otherwise null
     */
    @Nullable
    String get(String key, Dsn dsn);
}
