package io.sentry.config.provider;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A configuration provider that looks up the configuration in the JNDI registry on the standard location
 * {@code java:comp/env/sentry}.
 *
 * @see JndiSupport to check whether Jndi is available in the current JVM without needing to load this class
 */
public class JndiConfigurationProvider implements ConfigurationProvider {
    private static final String JNDI_PREFIX = "java:comp/env/sentry/";
    private static final Logger logger = LoggerFactory.getLogger(JndiConfigurationProvider.class);

    @Override
    public String getProperty(String key) {
        String value = null;
        try {
            Context ctx = new InitialContext();
            value = (String) ctx.lookup(JNDI_PREFIX + key);
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
