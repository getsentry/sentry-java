package net.kencochrane.raven;

import net.kencochrane.raven.connection.AsyncConnection;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.connection.HttpConnection;
import net.kencochrane.raven.connection.UdpConnection;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.HttpInterface;
import net.kencochrane.raven.event.interfaces.MessageInterface;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import net.kencochrane.raven.marshaller.Marshaller;
import net.kencochrane.raven.marshaller.json.*;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultRavenFactory extends RavenFactory {
    private static final Logger logger = Logger.getLogger(DefaultRavenFactory.class.getCanonicalName());

    @Override
    public Raven createRavenInstance(Dsn dsn) {
        Raven raven = new Raven();
        raven.setConnection(createConnection(dsn));
        return raven;
    }

    private Connection createConnection(Dsn dsn) {
        String protocol = dsn.getProtocol();
        Connection connection;

        if (protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https")) {
            logger.log(Level.INFO, "Using an HTTP connection to Sentry.");
            connection = createHttpConnection(dsn);
        } else if (protocol.equalsIgnoreCase("udp")) {
            logger.log(Level.INFO, "Using an UDP connection to Sentry.");
            connection = createUdpConnection(dsn);
        } else {
            throw new IllegalStateException("Couldn't create a connection for the protocol '" + protocol + "'");
        }

        if (dsn.getOptions().containsKey(Dsn.ASYNC_OPTION)) {
            connection = createAsyncConnection(dsn, connection);
        }

        return connection;
    }

    private Connection createAsyncConnection(Dsn dsn, Connection connection) {
        int maxThreads;
        if (dsn.getOptions().containsKey(AsyncConnection.DSN_MAX_THREADS_OPTION)) {
            maxThreads = Integer.parseInt(dsn.getOptions().get(AsyncConnection.DSN_MAX_THREADS_OPTION));
        } else {
            maxThreads = AsyncConnection.DEFAULT_MAX_THREADS;
        }

        int priority;
        if (dsn.getOptions().containsKey(AsyncConnection.DSN_PRIORITY_OPTION)) {
            priority = Integer.parseInt(dsn.getOptions().get(AsyncConnection.DSN_PRIORITY_OPTION));
        } else {
            priority = AsyncConnection.DEFAULT_PRIORITY;
        }

        return new AsyncConnection(connection, true, maxThreads, priority);
    }

    private Connection createHttpConnection(Dsn dsn) {
        HttpConnection httpConnection = new HttpConnection(HttpConnection.getSentryUrl(dsn),
                dsn.getPublicKey(), dsn.getSecretKey());
        httpConnection.setMarshaller(createMarshaller(dsn));

        // Set the naive mode
        httpConnection.setBypassSecurity(dsn.getProtocolSettings().contains(Dsn.NAIVE_PROTOCOL));
        // Set the HTTP timeout
        if (dsn.getOptions().containsKey(Dsn.TIMEOUT_OPTION))
            httpConnection.setTimeout(Integer.parseInt(dsn.getOptions().get(Dsn.TIMEOUT_OPTION)));
        return httpConnection;
    }

    private Connection createUdpConnection(Dsn dsn) {
        //String hostname, int port, String publicKey, String secretKey
        int port = dsn.getPort() != -1 ? dsn.getPort() : UdpConnection.DEFAULT_UDP_PORT;
        UdpConnection udpConnection = new UdpConnection(dsn.getHost(), port, dsn.getPublicKey(), dsn.getSecretKey());
        udpConnection.setMarshaller(createMarshaller(dsn));
        return udpConnection;
    }

    private Marshaller createMarshaller(Dsn dsn) {
        JsonMarshaller marshaller = new JsonMarshaller();

        // Set JSON marshaller bindings
        StackTraceInterfaceBinding stackTraceBinding = new StackTraceInterfaceBinding();
        //TODO: Set that properly
        stackTraceBinding.setRemoveCommonFramesWithEnclosing(true);
        //TODO: Add a way to remove in_app frames
        //stackTraceBinding.
        marshaller.addInterfaceBinding(StackTraceInterface.class, stackTraceBinding);
        marshaller.addInterfaceBinding(ExceptionInterface.class, new ExceptionInterfaceBinding(stackTraceBinding));
        marshaller.addInterfaceBinding(MessageInterface.class, new MessageInterfaceBinding());
        HttpInterfaceBinding httpBinding = new HttpInterfaceBinding();
        //TODO: Add a way to clean the HttpRequest
        //httpBinding.
        marshaller.addInterfaceBinding(HttpInterface.class, httpBinding);

        // Set compression
        marshaller.setCompression(!dsn.getOptions().containsKey(Dsn.NOCOMPRESSION_OPTION));

        return marshaller;
    }
}
