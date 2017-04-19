package io.sentry.dsn;

import javax.naming.Context;
import javax.naming.NamingException;
import java.util.Hashtable;

/**
 * JNDI initial context factory allowing to create custom mocked contexts.
 *
 * @see DsnTest#setUp()
 */
public class InitialContextFactory implements javax.naming.spi.InitialContextFactory {
    public static Context context;

    @Override
    public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
        return context;
    }
}
