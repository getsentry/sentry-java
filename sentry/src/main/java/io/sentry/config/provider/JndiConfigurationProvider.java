package io.sentry.config.provider;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A configuration provider that looks up the configuration in the JNDI registry.
 *
 * @see JndiSupport to check whether Jndi is available in the current JVM without needing to load this class
 */
public class JndiConfigurationProvider implements ConfigurationProvider {
    /**
     * The default prefix of the JNDI names of Sentry configuration. This, concatenated with the configuration key name
     * provided to the {@link #getProperty(String)} method, must form a valid JNDI name.
     */
    public static final String DEFAULT_JNDI_PREFIX = "java:comp/env/sentry/";

    private static final Logger logger = LoggerFactory.getLogger(JndiConfigurationProvider.class);

    private final String prefix;

    /**
     * Constructs a new instance using the {@link #DEFAULT_JNDI_PREFIX}.
     */
    public JndiConfigurationProvider() {
        this(DEFAULT_JNDI_PREFIX);
    }

    /**
     * Constructs a new instance that will look up the Sentry configuration keys using the provided prefix.
     *
     * @param jndiNamePrefix The prefix of the JNDI names of Sentry configuration. This, concatenated with
     *                       the configuration key name provided to the {@link #getProperty(String)} method, must form
     *                       a valid JNDI name.
     */
    public JndiConfigurationProvider(String jndiNamePrefix) {
        this.prefix = jndiNamePrefix;
    }

    @Override
    public String getProperty(String key) {
        String value = null;
        try {
            Context ctx = new InitialContext();
            value = (String) ctx.lookup(prefix + key);
        } catch (NoInitialContextException e) {
            logger.trace("JNDI not configured for Sentry (NoInitialContextEx)");
        } catch (NamingException e) {
            logger.trace("No " + prefix + key + " in JNDI");
        } catch (RuntimeException e) {
            logger.warn("Odd RuntimeException while testing for JNDI", e);
        }
        return value;
    }
}
