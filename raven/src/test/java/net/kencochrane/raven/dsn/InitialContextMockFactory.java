package net.kencochrane.raven.dsn;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.util.Hashtable;

/**
 * JNDI initial context factory allowing to create custom mocked contexts.
 *
 * @see DsnTest#setUp()
 */
public class InitialContextMockFactory implements InitialContextFactory {
    public static Context context;

    @Override
    public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
        return context;
    }
}
