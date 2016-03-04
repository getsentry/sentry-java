package com.getsentry.raven.appengine;

import com.google.appengine.api.utils.SystemProperty;
import com.getsentry.raven.DefaultRavenFactory;
import com.getsentry.raven.Raven;
import com.getsentry.raven.appengine.connection.AppEngineAsyncConnection;
import com.getsentry.raven.appengine.event.helper.AppEngineEventBuilderHelper;
import com.getsentry.raven.connection.Connection;
import com.getsentry.raven.dsn.Dsn;

/**
 * RavenFactory dedicated to create async connections within Google App Engine.
 */
public class AppEngineRavenFactory extends DefaultRavenFactory {
    /**
     * Option for the queue name used in Google App Engine of threads assigned for the connection.
     */
    public static final String QUEUE_NAME = "raven.async.gae.queuename";
    /**
     * Option to define the identifier of the async connection across every instance of the application.
     * <p>
     * It is important to set a different connection identifier for each opened connection to keep the uniqueness
     * of connection ID.
     * <p>
     * If the connection identifier is not specified, the system will define a connection identifier itself, but its
     * uniqueness within an instance isn't guaranteed.
     *
     * @see AppEngineAsyncConnection
     */
    public static final String CONNECTION_IDENTIFIER = "raven.async.gae.connectionid";

    @Override
    public Raven createRavenInstance(Dsn dsn) {
        Raven ravenInstance = super.createRavenInstance(dsn);
        ravenInstance.addBuilderHelper(new AppEngineEventBuilderHelper());
        return ravenInstance;
    }

    /**
     * Encapsulates an already existing connection in an {@link AppEngineAsyncConnection} and get the async options
     * from the Sentry DSN.
     *
     * @param dsn        Data Source Name of the Sentry server.
     * @param connection Connection to encapsulate in an {@link AppEngineAsyncConnection}.
     * @return the asynchronous connection.
     */
    @Override
    protected Connection createAsyncConnection(Dsn dsn, Connection connection) {
        String connectionIdentifier;
        if (dsn.getOptions().containsKey(CONNECTION_IDENTIFIER)) {
            connectionIdentifier = dsn.getOptions().get(CONNECTION_IDENTIFIER);
        } else {
            connectionIdentifier = AppEngineRavenFactory.class.getCanonicalName() + dsn + SystemProperty.version.get();
        }

        AppEngineAsyncConnection asyncConnection = new AppEngineAsyncConnection(connectionIdentifier, connection);

        if (dsn.getOptions().containsKey(QUEUE_NAME)) {
            asyncConnection.setQueue(dsn.getOptions().get(QUEUE_NAME));
        }

        return asyncConnection;
    }
}
