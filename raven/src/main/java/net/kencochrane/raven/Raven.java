package net.kencochrane.raven;

import net.kencochrane.raven.connection.AsyncConnection;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.connection.HttpConnection;
import net.kencochrane.raven.connection.UdpConnection;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.helper.EventBuilderHelper;
import net.kencochrane.raven.marshaller.json.JsonMarshaller;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Raven is a client for Sentry allowing to send an {@link Event} that will be processed and sent to a Sentry server.
 * <p>
 * A default client will use the protocol defined in the DSN and will send the content in the JSON format
 * (optionally compressed and encoded in base64).
 * </p>
 */
public class Raven {
    /**
     * Default charset used to send content to Sentry.
     * <p>
     * UTF-8 is used as the default since the communication by default is done through JSON
     * (which uses UTF-8 by default).
     * </p>
     */
    public static final String DEFAULT_CHARSET = "UTF-8";
    /**
     * Version of this client, the major version is the current supported Sentry protocol, the minor version changes
     * for each release of this project.
     */
    public static final String NAME = "Raven-Java/4.0";
    private static final Logger logger = Logger.getLogger(Raven.class.getCanonicalName());
    private final Set<EventBuilderHelper> builderHelpers = new HashSet<EventBuilderHelper>();
    private Connection connection;

    /**
     * Builds a default Raven client, trying to figure out which {@link Dsn} can be used.
     *
     * @see net.kencochrane.raven.Dsn#dsnLookup()
     */
    public Raven() {
        this(new Dsn());
    }

    /**
     * Builds a default Raven client using the given DSN.
     *
     * @param dsn Data Source Name as a String to use to connect to sentry.
     */
    public Raven(String dsn) {
        this(new Dsn(dsn));
    }

    /**
     * Builds a default Raven client using the given DSN.
     *
     * @param dsn Data Source Name as a String to use to connect to sentry.
     */
    public Raven(Dsn dsn) {
        this(determineConnection(dsn));
    }

    /**
     * Builds a Raven client using the given connection.
     *
     * @param connection connection to sentry.
     */
    public Raven(Connection connection) {
        this.connection = connection;
    }

    /**
     * Obtains the charset setting for the given DSN.
     * <p>
     * Defaults to {@link #DEFAULT_CHARSET} if the DSN doesn't specify a charset.
     * </p>
     *
     * @param dsn Data Source Name optionally containing a charset option.
     * @return the charset to use with the given DSN.
     */
    private static Charset determineCharset(Dsn dsn) {
        String charset = DEFAULT_CHARSET;

        if (dsn.getOptions().containsKey(Dsn.CHARSET_OPTION))
            charset = dsn.getOptions().get(Dsn.CHARSET_OPTION);

        return Charset.forName(charset);
    }

    /**
     * Builds a {@link Connection} based on a {@link Dsn}.
     * <p>
     * Currently supports the protocols HTTP(s) with {@link HttpConnection} and UPD with {@link UdpConnection}.
     * </p>
     *
     * @param dsn Data Source Name from which the connection will be generated.
     * @return a {@link Connection} allowing to send events to a Sentry server or {@code null} if nothing was found.
     */
    //TODO: Replace with a factory?
    private static Connection determineConnection(Dsn dsn) {
        String protocol = dsn.getProtocol();
        Connection connection = null;
        Charset charset = determineCharset(dsn);
        JsonMarshaller marshaller = new JsonMarshaller();
        marshaller.setCompression(!dsn.getOptions().containsKey(Dsn.NOCOMPRESSION_OPTION));
        marshaller.setCharset(charset);

        if (protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https")) {
            logger.log(Level.INFO, "Using an HTTP connection to Sentry.");
            HttpConnection httpConnection = new HttpConnection(dsn);
            httpConnection.setMarshaller(marshaller);
            connection = httpConnection;
        } else if (protocol.equalsIgnoreCase("udp")) {
            logger.log(Level.INFO, "Using an UDP connection to Sentry.");
            UdpConnection udpConnection = new UdpConnection(dsn);
            udpConnection.setCharset(charset);
            udpConnection.setMarshaller(marshaller);
            connection = udpConnection;
        } else {
            logger.log(Level.WARNING,
                    "Couldn't figure out automatically a connection to Sentry, one should be set manually");
        }

        if (dsn.getOptions().containsKey(Dsn.ASYNC_OPTION))
            connection = new AsyncConnection(connection, dsn);

        return connection;
    }

    /**
     * Runs the {@link EventBuilderHelper} against the {@link EventBuilder} to obtain additional information with a
     * MDC-like system.
     *
     * @param eventBuilder event builder containing a not yet finished event.
     */
    public void runBuilderHelpers(EventBuilder eventBuilder) {
        for (EventBuilderHelper builderHelper : builderHelpers) {
            builderHelper.helpBuildingEvent(eventBuilder);
        }
    }

    /**
     * Sends a built {@link Event} to the Sentry server.
     *
     * @param event event to send to Sentry.
     */
    public void sendEvent(Event event) {
        try {
            connection.send(event);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An exception occurred while sending the event to Sentry.", e);
        }
    }

    /**
     * Removes a builder helper.
     *
     * @param builderHelper builder helper to remove.
     */
    public void removeBuilderHelper(EventBuilderHelper builderHelper) {
        logger.log(Level.INFO, "Removes '" + builderHelper + "' to the list of builder helpers.");
        builderHelpers.remove(builderHelper);
    }

    /**
     * Adds a builder helper.
     *
     * @param builderHelper builder helper to add.
     */
    public void addBuilderHelper(EventBuilderHelper builderHelper) {
        logger.log(Level.INFO, "Adding '" + builderHelper + "' to the list of builder helpers.");
        builderHelpers.add(builderHelper);
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public Set<EventBuilderHelper> getBuilderHelpers() {
        return Collections.unmodifiableSet(builderHelpers);
    }
}
