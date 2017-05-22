package io.sentry.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;

/**
 * Handles JNDI lookup of a provided key within the Sentry namespace.
 */
public final class JndiLookup {
    /**
     * Lookup prefix for Sentry configuration in JNDI.
     */
    private static final String JNDI_PREFIX = "java:comp/env/sentry/";
    private static final Logger logger = LoggerFactory.getLogger(JndiLookup.class);

    private JndiLookup() {

    }

    /**
     * Looks up for a JNDI definition of the provided key.
     *
     * @param key name of configuration key, e.g. "dsn"
     * @return the value as defined in JNDI or null if it isn't defined.
     */
    public static String jndiLookup(String key) {
        String value = null;
        try {
            Context c = new InitialContext();
            value = (String) c.lookup(JNDI_PREFIX + key);
        } catch (NoInitialContextException e) {
            logger.trace("JNDI not configured for Sentry (NoInitialContextEx)");
        } catch (NamingException e) {
            logger.trace("No /sentry/" + key + " in JNDI");
        } catch (RuntimeException e) {
            logger.warn("Odd RuntimeException while testing for JNDI", e);
        }
        return value;
    }
}
