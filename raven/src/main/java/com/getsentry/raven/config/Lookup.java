package com.getsentry.raven.config;

import com.getsentry.raven.dsn.Dsn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle lookup of configuration keys by trying JNDI, System Environment, and Java System Properties.
 */
public final class Lookup {
    private static final Logger logger = LoggerFactory.getLogger(JndiLookup.class);

    private Lookup() {

    }

    /**
     * Attempt to lookup a configuration key.
     *
     * @param key name of configuration key, e.g. "dsn"
     * @return value of configuration key, if found, otherwise null
     */
    public static String lookup(String key) {
        String value = null;

        // Try to obtain from JNDI
        try {
            // Check that JNDI is available (not available on Android) by loading InitialContext
            Class.forName("javax.naming.InitialContext", false, Dsn.class.getClassLoader());
            value = JndiLookup.jndiLookup(key);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            logger.debug("JNDI not available", e);
        }

        // Use SENTRY_$KEY for Environment and Java System Properties
        String sentryfiedName = "SENTRY_" + key.toUpperCase();

        // Try to obtain from a System Environment Variable
        if (value == null) {
            value = System.getenv(sentryfiedName);
        }

        // Try to obtain from a Java System Property
        if (value == null) {
            value = System.getProperty(sentryfiedName);
        }

        if (value != null) {
            return value.trim();
        } else {
            return null;
        }
    }

}
