package io.sentry.appengine;

import com.google.appengine.api.utils.SystemProperty;
import io.sentry.DefaultSentryClientFactory;
import io.sentry.SentryClient;
import io.sentry.appengine.connection.AppEngineAsyncConnection;
import io.sentry.appengine.event.helper.AppEngineEventBuilderHelper;
import io.sentry.connection.Connection;
import io.sentry.dsn.Dsn;

/**
 * SentryFactory dedicated to create async connections within Google App Engine.
 */
public class AppEngineSentryClientFactory extends DefaultSentryClientFactory {
    /**
     * Option for the queue name used in Google App Engine of threads assigned for the connection.
     */
    public static final String QUEUE_NAME = "sentry.async.gae.queuename";
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
    public static final String CONNECTION_IDENTIFIER = "sentry.async.gae.connectionid";

    @Override
    public SentryClient createSentryInstance(Dsn dsn) {
        SentryClient sentryClientInstance = super.createSentryInstance(dsn);
        sentryClientInstance.addBuilderHelper(new AppEngineEventBuilderHelper());
        return sentryClientInstance;
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
            connectionIdentifier = AppEngineSentryClientFactory.class.getCanonicalName() + dsn + SystemProperty.version.get();
        }

        AppEngineAsyncConnection asyncConnection = new AppEngineAsyncConnection(connectionIdentifier, connection);

        if (dsn.getOptions().containsKey(QUEUE_NAME)) {
            asyncConnection.setQueue(dsn.getOptions().get(QUEUE_NAME));
        }

        return asyncConnection;
    }
}
