package net.kencochrane.raven.dsn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;

/**
 * JNDI Lookup allows to do a JNDI lookup without tying {@link Dsn} to the JNDI libraries.
 * <p>
 * Android does not support JNDI making the automatic lookup through JNDI illegal and fatal to the application.
 * Having the lookup in a separate class allows the classloader to load {@link Dsn} without exceptions.
 * </p>
 */
final class JndiLookup {
    /**
     * Lookup name for the DSN in JNDI.
     */
    private static final String JNDI_DSN_NAME = "java:comp/env/sentry/dsn";
    private static final Logger logger = LoggerFactory.getLogger(JndiLookup.class);

    private JndiLookup() {
    }

    /**
     * Looks up for a JNDI definition of the DSN.
     *
     * @return the DSN defined in JNDI or null if it isn't defined.
     */
    public static String jndiLookup() {
        String dsn = null;
        try {
            Context c = new InitialContext();
            dsn = (String) c.lookup(JNDI_DSN_NAME);
        } catch (NoInitialContextException e) {
            logger.debug("JNDI not configured for sentry (NoInitialContextEx)");
        } catch (NamingException e) {
            logger.debug("No /sentry/dsn in JNDI");
        } catch (RuntimeException e) {
            logger.warn("Odd RuntimeException while testing for JNDI", e);
        }
        return dsn;
    }
}
