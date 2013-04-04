package net.kencochrane.raven.connection;

import net.kencochrane.raven.Utils;
import org.apache.commons.codec.binary.Base64OutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;

/**
 * Abstract connection to a Sentry server.
 * <p>
 * Provide the basic tools to submit events to the server (authentication header, compression, base64encoding).
 * </p>
 */
public abstract class AbstractConnection implements Connection {
    /**
     * Current sentry protocol version.
     */
    public static final String SENTRY_PROTOCOL_VERSION = "3";
    private final Dsn dsn;
    private boolean compress = true;

    /**
     * Creates a connection based on a DSN.
     *
     * @param dsn Data Source Name of the sentry server.
     */
    protected AbstractConnection(Dsn dsn) {
        if (dsn == null)
            throw new IllegalArgumentException("The DSN must be not null for the connection to work");
        this.dsn = dsn;

        // Check if compression is disabled
        if (dsn.getOptions().containsKey(Dsn.COMPRESSION_OPTION))
            setCompress(Boolean.parseBoolean(dsn.getOptions().get(Dsn.COMPRESSION_OPTION)));
    }

    /**
     * Creates an authentication header for the sentry protocol.
     *
     * @return an authentication header as a String.
     */
    protected String getAuthHeader() {
        //TODO : Consider caching everything but the timestamp
        StringBuilder header = new StringBuilder();
        header.append("Sentry sentry_version=").append(SENTRY_PROTOCOL_VERSION);
        header.append(",sentry_client=").append(Utils.Client.NAME);
        header.append(",sentry_key=").append(dsn.getPublicKey());
        header.append(",sentry_secret=").append(dsn.getSecretKey());
        //TODO: Check sentry_timestamp
        return header.toString();
    }

    /**
     * Obtains an output stream where the data should be sent.
     *
     * @return an output stream where the data should be sent.
     * @throws IOException if the {@code OutputStream} couldn't be created.
     */
    protected OutputStream getOutput() throws IOException {
        OutputStream out = getOutputStream();

        // Compress if possible
        if (compress)
            out = new DeflaterOutputStream(out);

        // Encode in base64
        out = new Base64OutputStream(out);
        return out;

    }

    /**
     * Provides a raw {@code OutputStream} which will be the destination of the data.
     * <p>
     * To send events, use the stream provided by {@link #getOutput()}.
     * </p>
     *
     * @return a raw {@code OutputStream}.
     * @throws IOException if the raw {@code OutputStream} couldn't be created.
     */
    protected abstract OutputStream getOutputStream() throws IOException;

    /**
     * Get the Data Source Name used by the current connection.
     *
     * @return DSN for the current connection.
     */
    protected Dsn getDsn() {
        return dsn;
    }

    /**
     * Enables or disables the compression of the requests to Sentry.
     *
     * @param compress whether the compression should be enabled or not.
     */
    public void setCompress(boolean compress) {
        this.compress = compress;
    }
}
