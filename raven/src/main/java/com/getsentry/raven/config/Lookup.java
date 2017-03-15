package com.getsentry.raven.config;

import com.getsentry.raven.dsn.Dsn;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handle lookup of configuration keys by trying JNDI, System Environment, and Java System Properties.
 */
public final class Lookup {
    private static final Logger logger = Logger.getLogger(JndiLookup.class.getName());

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
            logger.log(Level.FINE, "JNDI not available", e);
        }

        // Try to obtain from a Java System Property
        if (value == null) {
            value = System.getProperty("sentry." + key.toLowerCase());
        }

        // Try to obtain from a System Environment Variable
        if (value == null) {
            value = System.getenv("SENTRY_" + key.toUpperCase());
        }

        if (value != null) {
            return value.trim();
        } else {
            return null;
        }
    }

}
