package net.kencochrane.raven;

import net.kencochrane.raven.connection.AsyncConnection;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.connection.HttpConnection;
import net.kencochrane.raven.connection.UdpConnection;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.helper.EventBuilderHelper;
import net.kencochrane.raven.marshaller.json.JsonMarshaller;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Raven {
    public static final String DEFAULT_CHARSET = "UTF-8";
    public static final String NAME = "Raven-Java/3.0";
    private static final Logger logger = Logger.getLogger(Raven.class.getCanonicalName());
    private final Set<EventBuilderHelper> builderHelpers = new HashSet<EventBuilderHelper>();
    private Connection connection;

    public Raven() {
        this(new Dsn());
    }

    public Raven(String dsn) {
        this(new Dsn(dsn));
    }

    public Raven(Dsn dsn) {
        this(determineConnection(dsn));
    }

    public Raven(Connection connection) {
        this.connection = connection;
    }

    private static Charset determineCharset(Dsn dsn) {
        String charset = DEFAULT_CHARSET;

        if (dsn.getOptions().containsKey(Dsn.CHARSET_OPTION))
            charset = dsn.getOptions().get(Dsn.CHARSET_OPTION);

        return Charset.forName(charset);
    }

    //TODO: Replace with a factory?
    private static Connection determineConnection(Dsn dsn) {
        String protocol = dsn.getProtocol();
        Connection connection = null;
        Charset charset = determineCharset(dsn);
        JsonMarshaller marshaller = new JsonMarshaller();
        marshaller.setCompression(!dsn.getOptions().containsKey(Dsn.NOCOMPRESSION_OPTION));
        marshaller.setCharset(charset);

        if (protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https")) {
            HttpConnection httpConnection = new HttpConnection(dsn);
            httpConnection.setMarshaller(marshaller);
            connection = httpConnection;
        } else if (protocol.equalsIgnoreCase("udp")) {
            UdpConnection udpConnection = new UdpConnection(dsn);
            udpConnection.setCharset(charset);
            udpConnection.setMarshaller(marshaller);
            connection = udpConnection;
        } else {
            logger.log(Level.WARNING,
                    "Couldn't figure out automatically a connection to Sentry, one should be set manually");
        }

        if (dsn.getOptions().containsKey(Dsn.ASYNC_OPTION))
            connection = new AsyncConnection(connection);

        return connection;
    }

    public void runBuilderHelpers(EventBuilder eventBuilder) {
        for (EventBuilderHelper builderHelper : builderHelpers) {
            builderHelper.helpBuildingEvent(eventBuilder);
        }
    }

    public void sendEvent(Event event) {
        try {
            connection.send(event);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An exception occurred while sending the event to Sentry.", e);
        }
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public Set<EventBuilderHelper> getBuilderHelpers() {
        return Collections.unmodifiableSet(builderHelpers);
    }

    public void removeBuilderHelper(EventBuilderHelper builderHelper) {
        builderHelpers.remove(builderHelper);
    }

    public void addBuilderHelper(EventBuilderHelper builderHelper) {
        builderHelpers.add(builderHelper);
    }
}
