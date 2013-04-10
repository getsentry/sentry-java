package net.kencochrane.raven;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.util.Hashtable;

public class InitialContextMockFactory implements InitialContextFactory {
    public static Context context;

    @Override
    public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
        return context;
    }
}
