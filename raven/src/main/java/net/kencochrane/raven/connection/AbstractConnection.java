package net.kencochrane.raven.connection;

import net.kencochrane.raven.Dsn;
import net.kencochrane.raven.Raven;

/**
 * Abstract connection to a Sentry server.
 * <p>
 * Provide the basic tools to submit events to the server (authentication header, dsn).
 * </p>
 */
public abstract class AbstractConnection implements Connection {
    /**
     * Current sentry protocol version.
     */
    public static final String SENTRY_PROTOCOL_VERSION = "3";
    private final Dsn dsn;

    /**
     * Creates a connection based on a DSN.
     *
     * @param dsn Data Source Name of the sentry server.
     */
    protected AbstractConnection(Dsn dsn) {
        if (dsn == null)
            throw new IllegalArgumentException("The DSN must be not null for the connection to work");
        this.dsn = dsn;
    }

    /**
     * Creates an authentication header for the sentry protocol.
     *
     * @return an authentication header as a String.
     */
    protected String getAuthHeader() {
        //TODO: Consider adding back signature? Not a priority, probably not worth it.
        StringBuilder header = new StringBuilder();
        header.append("Sentry sentry_version=").append(SENTRY_PROTOCOL_VERSION);
        header.append(",sentry_client=").append(Raven.NAME);
        header.append(",sentry_key=").append(dsn.getPublicKey());
        header.append(",sentry_secret=").append(dsn.getSecretKey());
        return header.toString();
    }

    /**
     * Get the Data Source Name used by the current connection.
     *
     * @return DSN for the current connection.
     */
    protected Dsn getDsn() {
        return dsn;
    }
}
