package net.kencochrane.raven.getsentry;

import net.kencochrane.raven.DefaultRavenFactory;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.dsn.Dsn;
import net.kencochrane.raven.getsentry.connection.GetSentryHttpsConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raven factory capable of handling 'getsentry' scheme, using the HTTPS connection to GetSentry.com.
 */
public class GetSentryRavenFactory extends DefaultRavenFactory {
    private static final Logger logger = LoggerFactory.getLogger(GetSentryRavenFactory.class);

    @Override
    protected Connection createConnection(Dsn dsn) {
        String protocol = dsn.getProtocol();
        Connection connection;

        if (protocol.equalsIgnoreCase("getsentry")) {
            logger.info("Using an HTTP connection to Sentry.");
            connection = createHttpConnection(dsn);

            // Enable async unless its value is 'false'.
            if (!Boolean.FALSE.toString().equalsIgnoreCase(dsn.getOptions().get(ASYNC_OPTION))) {
                connection = createAsyncConnection(dsn, connection);
            }
        } else {
            connection = super.createConnection(dsn);
        }

        return connection;
    }

    /**
     * Creates an HTTPS connection to the GetSentry server.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return a {@link GetSentryHttpsConnection} to the server.
     */
    protected Connection createHttpConnection(Dsn dsn) {
        GetSentryHttpsConnection httpConnection = new GetSentryHttpsConnection(dsn.getProjectId(), dsn.getPublicKey(),
                dsn.getSecretKey());
        httpConnection.setMarshaller(createMarshaller(dsn));

        // Set the HTTP timeout
        if (dsn.getOptions().containsKey(TIMEOUT_OPTION))
            httpConnection.setTimeout(Integer.parseInt(dsn.getOptions().get(TIMEOUT_OPTION)));
        return httpConnection;
    }
}
