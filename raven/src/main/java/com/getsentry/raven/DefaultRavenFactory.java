package com.getsentry.raven;

import com.getsentry.raven.buffer.Buffer;
import com.getsentry.raven.buffer.DiskBuffer;
import com.getsentry.raven.connection.*;
import com.getsentry.raven.dsn.Dsn;
import com.getsentry.raven.event.helper.ContextBuilderHelper;
import com.getsentry.raven.event.helper.HttpEventBuilderHelper;
import com.getsentry.raven.event.interfaces.*;
import com.getsentry.raven.marshaller.Marshaller;
import com.getsentry.raven.marshaller.json.*;
import com.getsentry.raven.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation of {@link RavenFactory}.
 * <p>
 * In most cases this is the implementation to use or extend for additional features.
 */
public class DefaultRavenFactory extends RavenFactory {
    //TODO: Add support for tags set by default
    /**
     * Protocol setting to disable security checks over an SSL connection.
     */
    public static final String NAIVE_PROTOCOL = "naive";
    /**
     * Option specific to raven-java, allowing to enable/disable the compression of requests to the Sentry Server.
     */
    public static final String COMPRESSION_OPTION = "raven.compression";
    /**
     * Option specific to raven-java, allowing to set maximum length of the message body in the requests to the
     * Sentry Server.
     */
    public static final String MAX_MESSAGE_LENGTH_OPTION = "raven.maxmessagelength";
    /**
     * Option specific to raven-java, allowing to set a timeout (in ms) for a request to the Sentry server.
     */
    public static final String TIMEOUT_OPTION = "raven.timeout";
    /**
     * Option to buffer events to disk when network is down.
     */
    public static final String BUFFER_DIR_OPTION = "raven.buffer.dir";
    /**
     * Option for maximum number of events to cache offline when network is down.
     */
    public static final String BUFFER_SIZE_OPTION = "raven.buffer.size";
    /**
     * Default number of events to cache offline when network is down.
     */
    public static final int BUFFER_SIZE_DEFAULT = 50;
    /**
     * Option for how long to wait between attempts to flush the disk buffer, in milliseconds.
     */
    public static final String BUFFER_FLUSHTIME_OPTION = "raven.buffer.flushtime";
    /**
     * Default number of milliseconds between attempts to flush buffered events.
     */
    public static final int BUFFER_FLUSHTIME_DEFAULT = 60000;
    /**
     * Option to disable the graceful shutdown of the buffer flusher.
     */
    public static final String BUFFER_GRACEFUL_SHUTDOWN_OPTION = "raven.buffer.gracefulshutdown";
    /**
     * Option for the graceful shutdown timeout of the buffer flushing executor, in milliseconds.
     */
    public static final String BUFFER_SHUTDOWN_TIMEOUT_OPTION = "raven.buffer.shutdowntimeout";
    /**
     * Option to send events asynchronously.
     */
    public static final String ASYNC_OPTION = "raven.async";
    /**
     * Option to disable the graceful shutdown of the async connection.
     */
    public static final String ASYNC_GRACEFUL_SHUTDOWN_OPTION = "raven.async.gracefulshutdown";
    /**
     * Option for the number of threads assigned for the connection.
     */
    public static final String ASYNC_THREADS_OPTION = "raven.async.threads";
    /**
     * Option for the priority of threads assigned for the connection.
     */
    public static final String ASYNC_PRIORITY_OPTION = "raven.async.priority";
    /**
     * Option for the maximum size of the queue.
     */
    public static final String ASYNC_QUEUE_SIZE_OPTION = "raven.async.queuesize";
    /**
     * Option for the maximum size of the queue.
     */
    public static final String ASYNC_QUEUE_OVERFLOW_OPTION = "raven.async.queue.overflow";
    /**
     * Option for the maximum size of the queue.
     */
    public static final String ASYNC_QUEUE_DISCARDOLD = "discardold";
    /**
     * Option for the maximum size of the queue.
     */
    public static final String ASYNC_QUEUE_DISCARDNEW = "discardnew";
    /**
     * Option for the maximum size of the queue.
     */
    public static final String ASYNC_QUEUE_BLOCK = "block";
    /**
     * Option for the graceful shutdown timeout of the async executor, in milliseconds.
     */
    public static final String ASYNC_SHUTDOWN_TIMEOUT_OPTION = "raven.async.shutdowntimeout";
    /**
     * Option to hide common stackframes with enclosing exceptions.
     */
    public static final String HIDE_COMMON_FRAMES_OPTION = "raven.stacktrace.hidecommon";
    /**
     * Option to set an HTTP proxy hostname for Sentry connections.
     */
    public static final String HTTP_PROXY_HOST_OPTION = "raven.http.proxy.host";
    /**
     * Option to set an HTTP proxy port for Sentry connections.
     */
    public static final String HTTP_PROXY_PORT_OPTION = "raven.http.proxy.port";
    /**
     * The default async queue size if none is provided.
     */
    public static final int QUEUE_SIZE_DEFAULT = 50;
    /**
     * The default HTTP proxy port to use if an HTTP Proxy hostname is set but port is not.
     */
    public static final int HTTP_PROXY_PORT_DEFAULT = 80;

    private static final Logger logger = LoggerFactory.getLogger(DefaultRavenFactory.class);
    private static final String FALSE = Boolean.FALSE.toString();

    private static final Map<String, RejectedExecutionHandler> REJECT_EXECUTION_HANDLERS = new HashMap();
    static {
        Map map = REJECT_EXECUTION_HANDLERS;
        map.put(ASYNC_QUEUE_BLOCK.toUpperCase(), new ThreadPoolExecutor.CallerRunsPolicy());
        map.put(ASYNC_QUEUE_DISCARDNEW.toUpperCase(), new ThreadPoolExecutor.DiscardPolicy());
        map.put(ASYNC_QUEUE_DISCARDOLD.toUpperCase(), new ThreadPoolExecutor.DiscardOldestPolicy());
        map.put(null, new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    /**
     * Get handler for tasks that cannot be executed by a {@link ThreadPoolExecutor}.
     *
     * @param dsn        Data Source Name of the Sentry server.
     * @return an {@link RejectedExecutionHandler} to the server.
     */
    protected RejectedExecutionHandler getRejectedExecutionHandler(Dsn dsn) {
        String value = dsn.getOptions().get(ASYNC_QUEUE_OVERFLOW_OPTION);
        value = value == null
                ? null
                : value.toUpperCase();
        RejectedExecutionHandler reh = REJECT_EXECUTION_HANDLERS.get(value);
        return (reh == null)
                ? REJECT_EXECUTION_HANDLERS.get(null)
                : reh;
    }

    @Override
    public Raven createRavenInstance(Dsn dsn) {
        Raven raven = new Raven(createConnection(dsn));
        try {
            // `ServletRequestListener` was added in the Servlet 2.4 API, and
            // is used as part of the `HttpEventBuilderHelper`, see:
            // https://tomcat.apache.org/tomcat-5.5-doc/servletapi/
            Class.forName("javax.servlet.ServletRequestListener", false, this.getClass().getClassLoader());
            raven.addBuilderHelper(new HttpEventBuilderHelper());
        } catch (ClassNotFoundException e) {
            logger.debug("The current environment doesn't provide access to servlets,"
                         + "or provides an unsupported version.");
        }
        raven.addBuilderHelper(new ContextBuilderHelper(raven));
        return raven;
    }

    /**
     * Creates a connection to the given DSN by determining the protocol.
     *
     * @param dsn Data Source Name of the Sentry server to use.
     * @return a connection to the server.
     */
    protected Connection createConnection(Dsn dsn) {
        String protocol = dsn.getProtocol();
        Connection connection;

        if (protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https")) {
            logger.info("Using an HTTP connection to Sentry.");
            connection = createHttpConnection(dsn);
        } else if (protocol.equalsIgnoreCase("out")) {
            logger.info("Using StdOut to send events.");
            connection = createStdOutConnection(dsn);
        } else if (protocol.equalsIgnoreCase("noop")) {
            logger.info("Using noop to send events.");
            connection = new NoopConnection();
        } else {
            throw new IllegalStateException("Couldn't create a connection for the protocol '" + protocol + "'");
        }

        String bufferDir = dsn.getOptions().get(BUFFER_DIR_OPTION);
        if (bufferDir != null) {
            int bufferSize = Util.parseInteger(dsn.getOptions().get(BUFFER_SIZE_OPTION), BUFFER_SIZE_DEFAULT);
            long flushtime = Util.parseLong(dsn.getOptions().get(BUFFER_FLUSHTIME_OPTION), BUFFER_FLUSHTIME_DEFAULT);
            boolean gracefulShutdown = !FALSE.equalsIgnoreCase(dsn.getOptions().get(BUFFER_GRACEFUL_SHUTDOWN_OPTION));
            Buffer eventBuffer = new DiskBuffer(new File(bufferDir), bufferSize);

            String shutdownTimeoutStr = dsn.getOptions().get(BUFFER_SHUTDOWN_TIMEOUT_OPTION);
            if (shutdownTimeoutStr != null) {
                long shutdownTimeout = Long.parseLong(shutdownTimeoutStr);
                connection = new BufferedConnection(connection, eventBuffer, flushtime, gracefulShutdown,
                    shutdownTimeout);
            } else {
                connection = new BufferedConnection(connection, eventBuffer, flushtime, gracefulShutdown);
            }
        }

        // Enable async unless its value is 'false'.
        if (!FALSE.equalsIgnoreCase(dsn.getOptions().get(ASYNC_OPTION))) {
            connection = createAsyncConnection(dsn, connection);
        }

        return connection;
    }

    /**
     * Encapsulates an already existing connection in an {@link AsyncConnection} and get the async options from the
     * Sentry DSN.
     *
     * @param dsn        Data Source Name of the Sentry server.
     * @param connection Connection to encapsulate in an {@link AsyncConnection}.
     * @return the asynchronous connection.
     */
    protected Connection createAsyncConnection(Dsn dsn, Connection connection) {

        int maxThreads;
        if (dsn.getOptions().containsKey(ASYNC_THREADS_OPTION)) {
            maxThreads = Integer.parseInt(dsn.getOptions().get(ASYNC_THREADS_OPTION));
        } else {
            maxThreads = Runtime.getRuntime().availableProcessors();
        }

        int priority;
        if (dsn.getOptions().containsKey(ASYNC_PRIORITY_OPTION)) {
            priority = Integer.parseInt(dsn.getOptions().get(ASYNC_PRIORITY_OPTION));
        } else {
            priority = Thread.MIN_PRIORITY;
        }

        BlockingDeque<Runnable> queue;
        if (dsn.getOptions().containsKey(ASYNC_QUEUE_SIZE_OPTION)) {
            int queueSize = Integer.parseInt(dsn.getOptions().get(ASYNC_QUEUE_SIZE_OPTION));
            if (queueSize == -1) {
                queue = new LinkedBlockingDeque<>();
            } else {
                queue = new LinkedBlockingDeque<>(queueSize);
            }
        } else {
            queue = new LinkedBlockingDeque<>(QUEUE_SIZE_DEFAULT);
        }

        ExecutorService executorService = new ThreadPoolExecutor(
                maxThreads, maxThreads, 0L, TimeUnit.MILLISECONDS, queue,
                new DaemonThreadFactory(priority), getRejectedExecutionHandler(dsn));

        boolean gracefulShutdown = !FALSE.equalsIgnoreCase(dsn.getOptions().get(ASYNC_GRACEFUL_SHUTDOWN_OPTION));

        String shutdownTimeoutStr = dsn.getOptions().get(ASYNC_SHUTDOWN_TIMEOUT_OPTION);
        if (shutdownTimeoutStr != null) {
            long shutdownTimeout = Long.parseLong(shutdownTimeoutStr);
            return new AsyncConnection(connection, executorService, gracefulShutdown, shutdownTimeout);
        } else {
            return new AsyncConnection(connection, executorService, gracefulShutdown);
        }
    }

    /**
     * Creates an HTTP connection to the Sentry server.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return an {@link HttpConnection} to the server.
     */
    protected Connection createHttpConnection(Dsn dsn) {
        URL sentryApiUrl = HttpConnection.getSentryApiUrl(dsn.getUri(), dsn.getProjectId());

        String proxyHost = null;
        if (dsn.getOptions().containsKey(HTTP_PROXY_HOST_OPTION)) {
            proxyHost = dsn.getOptions().get(HTTP_PROXY_HOST_OPTION);
        }

        int proxyPort = HTTP_PROXY_PORT_DEFAULT;
        if (dsn.getOptions().containsKey(HTTP_PROXY_PORT_OPTION)) {
            proxyPort = Integer.parseInt(dsn.getOptions().get(HTTP_PROXY_PORT_OPTION));
        }

        Proxy proxy = null;
        if (proxyHost != null) {
            InetSocketAddress proxyAddr = new InetSocketAddress(proxyHost, proxyPort);
            proxy = new Proxy(Proxy.Type.HTTP, proxyAddr);
        }

        HttpConnection httpConnection = new HttpConnection(sentryApiUrl, dsn.getPublicKey(), dsn.getSecretKey(), proxy);
        httpConnection.setMarshaller(createMarshaller(dsn));

        // Set the naive mode
        httpConnection.setBypassSecurity(dsn.getProtocolSettings().contains(NAIVE_PROTOCOL));
        // Set the HTTP timeout
        if (dsn.getOptions().containsKey(TIMEOUT_OPTION))
            httpConnection.setTimeout(Integer.parseInt(dsn.getOptions().get(TIMEOUT_OPTION)));
        return httpConnection;
    }

    /**
     * Uses stdout to send the logs.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return an {@link OutputStreamConnection} using {@code System.out}.
     */
    protected Connection createStdOutConnection(Dsn dsn) {
        //CHECKSTYLE.OFF: RegexpSinglelineJava
        OutputStreamConnection stdOutConnection = new OutputStreamConnection(System.out);
        //CHECKSTYLE.ON: RegexpSinglelineJava
        stdOutConnection.setMarshaller(createMarshaller(dsn));
        return stdOutConnection;
    }

    /**
     * Creates a JSON marshaller that will convert every {@link com.getsentry.raven.event.Event} in a format
     * handled by the Sentry server.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return a {@link JsonMarshaller} to process the events.
     */
    protected Marshaller createMarshaller(Dsn dsn) {
        int maxMessageLength = Util.parseInteger(
                dsn.getOptions().get(MAX_MESSAGE_LENGTH_OPTION), JsonMarshaller.DEFAULT_MAX_MESSAGE_LENGTH);
        JsonMarshaller marshaller = new JsonMarshaller(maxMessageLength);

        // Set JSON marshaller bindings
        StackTraceInterfaceBinding stackTraceBinding = new StackTraceInterfaceBinding();
        // Enable common frames hiding unless its value is 'false'.
        stackTraceBinding.setRemoveCommonFramesWithEnclosing(
                !FALSE.equalsIgnoreCase(dsn.getOptions().get(HIDE_COMMON_FRAMES_OPTION)));
        stackTraceBinding.setNotInAppFrames(getNotInAppFrames());

        marshaller.addInterfaceBinding(StackTraceInterface.class, stackTraceBinding);
        marshaller.addInterfaceBinding(ExceptionInterface.class, new ExceptionInterfaceBinding(stackTraceBinding));
        marshaller.addInterfaceBinding(MessageInterface.class, new MessageInterfaceBinding(maxMessageLength));
        marshaller.addInterfaceBinding(UserInterface.class, new UserInterfaceBinding());
        HttpInterfaceBinding httpBinding = new HttpInterfaceBinding();
        //TODO: Add a way to clean the HttpRequest
        //httpBinding.
        marshaller.addInterfaceBinding(HttpInterface.class, httpBinding);

        // Enable compression unless the option is set to false
        marshaller.setCompression(!FALSE.equalsIgnoreCase(dsn.getOptions().get(COMPRESSION_OPTION)));

        return marshaller;
    }

    /**
     * Provides a list of package names to consider as "not in-app".
     * <p>
     * Those packages will be used with the {@link StackTraceInterface} to hide frames that aren't a part of
     * the main application.
     *
     * @return the list of "not in-app" packages.
     */
    protected Collection<String> getNotInAppFrames() {
        return Arrays.asList("com.sun.",
                "java.",
                "javax.",
                "org.omg.",
                "sun.",
                "junit.",
                "com.intellij.rt.");
    }

    /**
     * Thread factory generating daemon threads with a custom priority.
     * <p>
     * Those (usually) low priority threads will allow to send event details to sentry concurrently without slowing
     * down the main application.
     */
    @SuppressWarnings("PMD.AvoidThreadGroup")
    protected static final class DaemonThreadFactory implements ThreadFactory {
        private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final int priority;

        private DaemonThreadFactory(int priority) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            namePrefix = "raven-pool-" + POOL_NUMBER.getAndIncrement() + "-thread-";
            this.priority = priority;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (!t.isDaemon())
                t.setDaemon(true);
            if (t.getPriority() != priority)
                t.setPriority(priority);
            return t;
        }
    }
}
