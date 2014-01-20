package net.kencochrane.raven.appengine;

import net.kencochrane.raven.DefaultRavenFactory;
import net.kencochrane.raven.appengine.connection.AppEngineAsyncConnection;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.dsn.Dsn;

public class AppEngineRavenFactory extends DefaultRavenFactory {
    /**
     * Option for the queue name used in Google App Engine of threads assigned for the connection.
     */
    public static final String QUEUE_NAME = "raven.async.gaequeuename";

    /**
     * Encapsulates an already existing connection in an {@link net.kencochrane.raven.appengine.connection.AppEngineAsyncConnection} and get the async options
     * from the Sentry DSN.
     *
     * @param dsn        Data Source Name of the Sentry server.
     * @param connection Connection to encapsulate in an {@link net.kencochrane.raven.appengine.connection.AppEngineAsyncConnection}.
     * @return the asynchronous connection.
     */
    @Override
    protected Connection createAsyncConnection(Dsn dsn, Connection connection) {
        AppEngineAsyncConnection asyncConnection = new AppEngineAsyncConnection(connection);

        if (dsn.getOptions().containsKey(QUEUE_NAME)) {
            asyncConnection.setQueue(dsn.getOptions().get(QUEUE_NAME));
        }

        return asyncConnection;
    }
}
