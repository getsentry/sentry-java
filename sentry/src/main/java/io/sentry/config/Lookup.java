package io.sentry.config;

import io.sentry.dsn.Dsn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Handle lookup of configuration keys by trying JNDI, System Environment, and Java System Properties.
 */
public final class Lookup {
    private static final Logger logger = LoggerFactory.getLogger(Lookup.class);

    /**
     * The default filename of the Sentry configuration file.
     */
    private static final String DEFAULT_CONFIG_FILE_NAME = "sentry.properties";
    /**
     * Properties loaded from the Sentry configuration file, or null if no file was
     * found or it failed to parse.
     */
    private final Properties configProps;

    /**
     * Construct a lookup object using the default Sentry configuration file name.
     */
    public Lookup() {
        this(DEFAULT_CONFIG_FILE_NAME);
    }

    /**
     * Construct a lookup object using the provided Sentry configuration file name.
     *
     * @param configFileName file name of configuration file to load from resources
     */
    public Lookup(String configFileName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream input = classLoader.getResourceAsStream(configFileName);

        if (input != null) {
            configProps = new Properties();
            try {
                configProps.load(input);
            } catch (IOException e) {
                logger.error("Error loading Sentry configuration file '{}' file: ", configFileName, e);
            }
        } else {
            configProps = null;
            logger.debug("Sentry configuration file '{}' not found.", configFileName);
        }
    }

    /**
     * Attempt to lookup a configuration key, without checking any DSN options.
     *
     * @param key name of configuration key, e.g. "dsn"
     * @return value of configuration key, if found, otherwise null
     */
    public String lookup(String key) {
        return lookup(key, null);
    }

    /**
     * Attempt to lookup a configuration key using the following order:
     *
     * 1. JNDI, if available
     * 2. Java System Properties
     * 3. System Environment Variables
     * 4. DSN options, if a non-null DSN is provided
     * 5. Sentry properties file found in resources
     *
     * @param key name of configuration key, e.g. "dsn"
     * @param dsn an optional DSN to retrieve options from
     * @return value of configuration key, if found, otherwise null
     */
    public String lookup(String key, Dsn dsn) {
        String value = null;

        // Try to obtain from JNDI
        try {
            // Check that JNDI is available (not available on Android) by loading InitialContext
            Class.forName("javax.naming.InitialContext", false, Dsn.class.getClassLoader());
            value = JndiLookup.jndiLookup(key);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            logger.debug("JNDI not available", e);
        }

        // Try to obtain from a Java System Property
        if (value == null) {
            value = System.getProperty("sentry." + key.toLowerCase());
        }

        // Try to obtain from a System Environment Variable
        if (value == null) {
            value = System.getenv("SENTRY_" + key.replace(".", "_").toUpperCase());
        }

        // Try to obtain from the provided DSN, if set
        if (value == null && dsn != null) {
            value = dsn.getOptions().get(key);
        }

        // Try to obtain from config file
        if (value == null && configProps != null) {
            value = configProps.getProperty(value);
        }

        if (value != null) {
            return value.trim();
        } else {
            return null;
        }
    }

}
