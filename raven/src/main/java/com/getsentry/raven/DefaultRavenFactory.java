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
import java.net.Authenticator;
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
     * Option for whether to compress requests sent to the Sentry Server.
     */
    public static final String COMPRESSION_OPTION = "raven.compression";
    /**
     * Option to set the maximum length of the message body in the requests to the
     * Sentry Server.
     */
    public static final String MAX_MESSAGE_LENGTH_OPTION = "raven.maxmessagelength";
    /**
     * Option to set a timeout for requests to the Sentry server, in milliseconds.
     */
    public static final String TIMEOUT_OPTION = "raven.timeout";
    /**
     * Default timeout of an HTTP connection to Sentry.
     */
    public static final int TIMEOUT_DEFAULT = (int) TimeUnit.SECONDS.toMillis(1);
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
    public static final long BUFFER_FLUSHTIME_DEFAULT = 60000;
    /**
     * Option to disable the graceful shutdown of the buffer flusher.
     */
    public static final String BUFFER_GRACEFUL_SHUTDOWN_OPTION = "raven.buffer.gracefulshutdown";
    /**
     * Option for the graceful shutdown timeout of the buffer flushing executor, in milliseconds.
     */
    public static final String BUFFER_SHUTDOWN_TIMEOUT_OPTION = "raven.buffer.shutdowntimeout";
    /**
     * Default timeout of the {@link BufferedConnection} shutdown, in milliseconds.
     */
    public static final long BUFFER_SHUTDOWN_TIMEOUT_DEFAULT = TimeUnit.SECONDS.toMillis(1);
    /**
     * Option for whether to send events asynchronously.
     */
    public static final String ASYNC_OPTION = "raven.async";
    /**
     * Option to disable the graceful shutdown of the async connection.
     */
    public static final String ASYNC_GRACEFUL_SHUTDOWN_OPTION = "raven.async.gracefulshutdown";
    /**
     * Option for the number of threads used for the async connection.
     */
    public static final String ASYNC_THREADS_OPTION = "raven.async.threads";
    /**
     * Option for the priority of threads used for the async connection.
     */
    public static final String ASYNC_PRIORITY_OPTION = "raven.async.priority";
    /**
     * Option for the maximum size of the async send queue.
     */
    public static final String ASYNC_QUEUE_SIZE_OPTION = "raven.async.queuesize";
    /**
     * Option for what to do when the async executor queue is full.
     */
    public static final String ASYNC_QUEUE_OVERFLOW_OPTION = "raven.async.queue.overflow";
    /**
     * Async executor overflow behavior that will discard old events in the queue.
     */
    public static final String ASYNC_QUEUE_DISCARDOLD = "discardold";
    /**
     * Async executor overflow behavior that will discard the new event that was attempting
     * to be sent.
     */
    public static final String ASYNC_QUEUE_DISCARDNEW = "discardnew";
    /**
     * Async executor overflow behavior that will cause a synchronous send to occur on the
     * current thread.
     */
    public static final String ASYNC_QUEUE_SYNC = "sync";
    /**
     * Default behavior to use when the async executor queue is full.
     */
    public static final String ASYNC_QUEUE_OVERFLOW_DEFAULT = ASYNC_QUEUE_DISCARDOLD;
    /**
     * Option for the graceful shutdown timeout of the async executor, in milliseconds.
     */
    public static final String ASYNC_SHUTDOWN_TIMEOUT_OPTION = "raven.async.shutdowntimeout";
    /**
     * Default timeout of the {@link AsyncConnection} executor, in milliseconds.
     */
    public static final long ASYNC_SHUTDOWN_TIMEOUT_DEFAULT = TimeUnit.SECONDS.toMillis(1);
    /**
     * Option for whether to hide common stackframes with enclosing exceptions.
     */
    public static final String HIDE_COMMON_FRAMES_OPTION = "raven.stacktrace.hidecommon";
    /**
     * Option for whether to sample events, allowing from 0.0 to 1.0 (0 to 100%) to be sent to the server.
     */
    public static final String SAMPLE_RATE_OPTION = "raven.sample.rate";
    /**
     * Option to set an HTTP proxy hostname for Sentry connections.
     */
    public static final String HTTP_PROXY_HOST_OPTION = "raven.http.proxy.host";
    /**
     * Option to set an HTTP proxy port for Sentry connections.
     */
    public static final String HTTP_PROXY_PORT_OPTION = "raven.http.proxy.port";
    /**
     * Option to set an HTTP proxy username for Sentry connections.
     */
    public static final String HTTP_PROXY_USER_OPTION = "raven.http.proxy.user";
    /**
     * Option to set an HTTP proxy password for Sentry connections.
     */
    public static final String HTTP_PROXY_PASS_OPTION = "raven.http.proxy.password";
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

    private static final Map<String, RejectedExecutionHandler> REJECT_EXECUTION_HANDLERS = new HashMap<>();
    static {
        REJECT_EXECUTION_HANDLERS.put(ASYNC_QUEUE_SYNC, new ThreadPoolExecutor.CallerRunsPolicy());
        REJECT_EXECUTION_HANDLERS.put(ASYNC_QUEUE_DISCARDNEW, new ThreadPoolExecutor.DiscardPolicy());
        REJECT_EXECUTION_HANDLERS.put(ASYNC_QUEUE_DISCARDOLD, new ThreadPoolExecutor.DiscardOldestPolicy());
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

        Buffer eventBuffer = getBuffer(dsn);
        if (eventBuffer != null) {
            long flushtime = getBufferFlushtime(dsn);
            boolean gracefulShutdown = getBufferedConnectionGracefulShutdownEnabled(dsn);
            Long shutdownTimeout = getBufferedConnectionShutdownTimeout(dsn);
            connection = new BufferedConnection(connection, eventBuffer, flushtime, gracefulShutdown,
                shutdownTimeout);
        }

        // Enable async unless its value is 'false'.
        if (getAsyncEnabled(dsn)) {
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

        int maxThreads = getAsyncThreads(dsn);
        int priority = getAsyncPriority(dsn);

        BlockingDeque<Runnable> queue;
        int queueSize = getAsyncQueueSize(dsn);
        if (queueSize == -1) {
            queue = new LinkedBlockingDeque<>();
        } else {
            queue = new LinkedBlockingDeque<>(queueSize);
        }

        ExecutorService executorService = new ThreadPoolExecutor(
            maxThreads, maxThreads, 0L, TimeUnit.MILLISECONDS, queue,
            new DaemonThreadFactory(priority), getRejectedExecutionHandler(dsn));

        boolean gracefulShutdown = getAsyncGracefulShutdownEnabled(dsn);

        long shutdownTimeout = getAsyncShutdownTimeout(dsn);
        return new AsyncConnection(connection, executorService, gracefulShutdown, shutdownTimeout);
    }

    /**
     * Creates an HTTP connection to the Sentry server.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return an {@link HttpConnection} to the server.
     */
    protected Connection createHttpConnection(Dsn dsn) {
        URL sentryApiUrl = HttpConnection.getSentryApiUrl(dsn.getUri(), dsn.getProjectId());

        String proxyHost = getProxyHost(dsn);
        String proxyUser = getProxyUser(dsn);
        String proxyPass = getProxyPass(dsn);
        int proxyPort = getProxyPort(dsn);

        Proxy proxy = null;
        if (proxyHost != null) {
            InetSocketAddress proxyAddr = new InetSocketAddress(proxyHost, proxyPort);
            proxy = new Proxy(Proxy.Type.HTTP, proxyAddr);
            if (proxyUser != null && proxyPass != null) {
                Authenticator.setDefault(new ProxyAuthenticator(proxyUser, proxyPass));
            }
        }

        Double sampleRate = getSampleRate(dsn);
        EventSampler eventSampler = null;
        if (sampleRate != null) {
            eventSampler = new RandomEventSampler(sampleRate);
        }

        HttpConnection httpConnection = new HttpConnection(sentryApiUrl, dsn.getPublicKey(),
            dsn.getSecretKey(), proxy, eventSampler);

        Marshaller marshaller = createMarshaller(dsn);
        httpConnection.setMarshaller(marshaller);

        int timeout = getTimeout(dsn);
        httpConnection.setTimeout(timeout);

        boolean bypassSecurityEnabled = getBypassSecurityEnabled(dsn);
        httpConnection.setBypassSecurity(bypassSecurityEnabled);

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
        int maxMessageLength = getMaxMessageLength(dsn);
        JsonMarshaller marshaller = new JsonMarshaller(maxMessageLength);

        // Set JSON marshaller bindings
        StackTraceInterfaceBinding stackTraceBinding = new StackTraceInterfaceBinding();
        // Enable common frames hiding unless its value is 'false'.
        stackTraceBinding.setRemoveCommonFramesWithEnclosing(getHideCommonFramesEnabled(dsn));
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
        marshaller.setCompression(getCompressionEnabled(dsn));

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
     * Whether or not to wrap the underlying connection in an {@link AsyncConnection}.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Whether or not to wrap the underlying connection in an {@link AsyncConnection}.
     */
    protected boolean getAsyncEnabled(Dsn dsn) {
        return !FALSE.equalsIgnoreCase(dsn.getOptions().get(ASYNC_OPTION));
    }

    /**
     * Handler for tasks that cannot be immediately queued by a {@link ThreadPoolExecutor}.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Handler for tasks that cannot be immediately queued by a {@link ThreadPoolExecutor}.
     */
    protected RejectedExecutionHandler getRejectedExecutionHandler(Dsn dsn) {
        String overflowName = ASYNC_QUEUE_OVERFLOW_DEFAULT;
        if (dsn.getOptions().containsKey(ASYNC_QUEUE_OVERFLOW_OPTION)) {
            overflowName = dsn.getOptions().get(ASYNC_QUEUE_OVERFLOW_OPTION).toLowerCase();
        }

        RejectedExecutionHandler handler = REJECT_EXECUTION_HANDLERS.get(overflowName);
        if (handler == null) {
            String options = Arrays.toString(REJECT_EXECUTION_HANDLERS.keySet().toArray());
            throw new RuntimeException("RejectedExecutionHandler not found: '" + overflowName
                + "', valid choices are: " + options);
        }

        return handler;
    }

    /**
     * Maximum time to wait for {@link BufferedConnection} shutdown when closed, in milliseconds.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Maximum time to wait for {@link BufferedConnection} shutdown when closed, in milliseconds.
     */
    protected long getBufferedConnectionShutdownTimeout(Dsn dsn) {
        return Util.parseLong(dsn.getOptions().get(BUFFER_SHUTDOWN_TIMEOUT_OPTION), BUFFER_SHUTDOWN_TIMEOUT_DEFAULT);
    }

    /**
     * Whether or not to attempt a graceful shutdown of the {@link BufferedConnection} upon close.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Whether or not to attempt a graceful shutdown of the {@link BufferedConnection} upon close.
     */
    protected boolean getBufferedConnectionGracefulShutdownEnabled(Dsn dsn) {
        return !FALSE.equalsIgnoreCase(dsn.getOptions().get(BUFFER_GRACEFUL_SHUTDOWN_OPTION));
    }

    /**
     * How long to wait between attempts to flush the disk buffer, in milliseconds.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return ow long to wait between attempts to flush the disk buffer, in milliseconds.
     */
    protected long getBufferFlushtime(Dsn dsn) {
        return Util.parseLong(dsn.getOptions().get(BUFFER_FLUSHTIME_OPTION), BUFFER_FLUSHTIME_DEFAULT);
    }

    /**
     * The graceful shutdown timeout of the async executor, in milliseconds.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return The graceful shutdown timeout of the async executor, in milliseconds.
     */
    protected long getAsyncShutdownTimeout(Dsn dsn) {
        return Util.parseLong(dsn.getOptions().get(ASYNC_SHUTDOWN_TIMEOUT_OPTION), ASYNC_SHUTDOWN_TIMEOUT_DEFAULT);
    }

    /**
     * Whether or not to attempt the graceful shutdown of the {@link AsyncConnection} upon close.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Whether or not to attempt the graceful shutdown of the {@link AsyncConnection} upon close.
     */
    protected boolean getAsyncGracefulShutdownEnabled(Dsn dsn) {
        return !FALSE.equalsIgnoreCase(dsn.getOptions().get(ASYNC_GRACEFUL_SHUTDOWN_OPTION));
    }

    /**
     * Maximum size of the async send queue.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Maximum size of the async send queue.
     */
    protected int getAsyncQueueSize(Dsn dsn) {
        return Util.parseInteger(dsn.getOptions().get(ASYNC_QUEUE_SIZE_OPTION), QUEUE_SIZE_DEFAULT);
    }

    /**
     * Priority of threads used for the async connection.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Priority of threads used for the async connection.
     */
    protected int getAsyncPriority(Dsn dsn) {
        return Util.parseInteger(dsn.getOptions().get(ASYNC_PRIORITY_OPTION), Thread.MIN_PRIORITY);
    }

    /**
     * The number of threads used for the async connection.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return The number of threads used for the async connection.
     */
    protected int getAsyncThreads(Dsn dsn) {
        return Util.parseInteger(dsn.getOptions().get(ASYNC_THREADS_OPTION),
            Runtime.getRuntime().availableProcessors());
    }

    /**
     * Whether to disable security checks over an SSL connection.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Whether to disable security checks over an SSL connection.
     */
    protected boolean getBypassSecurityEnabled(Dsn dsn) {
        return dsn.getProtocolSettings().contains(NAIVE_PROTOCOL);
    }

    /**
     * Whether to sample events, and if so how much to allow through to the server (from 0.0 to 1.0).
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return The ratio of events to allow through to server, or null if sampling is disabled.
     */
    protected Double getSampleRate(Dsn dsn) {
        return Util.parseDouble(dsn.getOptions().get(SAMPLE_RATE_OPTION), null);
    }

    /**
     * HTTP proxy port for Sentry connections.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return HTTP proxy port for Sentry connections.
     */
    protected int getProxyPort(Dsn dsn) {
        return Util.parseInteger(dsn.getOptions().get(HTTP_PROXY_PORT_OPTION), HTTP_PROXY_PORT_DEFAULT);
    }

    /**
     * HTTP proxy hostname for Sentry connections.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return HTTP proxy hostname for Sentry connections.
     */
    protected String getProxyHost(Dsn dsn) {
        return dsn.getOptions().get(HTTP_PROXY_HOST_OPTION);
    }

    /**
     * HTTP proxy username for Sentry connections.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return HTTP proxy username for Sentry connections.
     */
    protected String getProxyUser(Dsn dsn) {
        return dsn.getOptions().get(HTTP_PROXY_USER_OPTION);
    }

    /**
     * HTTP proxy password for Sentry connections.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return HTTP proxy password for Sentry connections.
     */
    protected String getProxyPass(Dsn dsn) {
        return dsn.getOptions().get(HTTP_PROXY_PASS_OPTION);
    }

    /**
     * Whether to compress requests sent to the Sentry Server.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Whether to compress requests sent to the Sentry Server.
     */
    protected boolean getCompressionEnabled(Dsn dsn) {
        return !FALSE.equalsIgnoreCase(dsn.getOptions().get(COMPRESSION_OPTION));
    }

    /**
     * Whether to hide common stackframes with enclosing exceptions.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Whether to hide common stackframes with enclosing exceptions.
     */
    protected boolean getHideCommonFramesEnabled(Dsn dsn) {
        return !FALSE.equalsIgnoreCase(dsn.getOptions().get(HIDE_COMMON_FRAMES_OPTION));
    }

    /**
     * The maximum length of the message body in the requests to the Sentry Server.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return The maximum length of the message body in the requests to the Sentry Server.
     */
    protected int getMaxMessageLength(Dsn dsn) {
        return Util.parseInteger(
            dsn.getOptions().get(MAX_MESSAGE_LENGTH_OPTION), JsonMarshaller.DEFAULT_MAX_MESSAGE_LENGTH);
    }

    /**
     * Timeout for requests to the Sentry server, in milliseconds.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Timeout for requests to the Sentry server, in milliseconds.
     */
    protected int getTimeout(Dsn dsn) {
        return Util.parseInteger(dsn.getOptions().get(TIMEOUT_OPTION), TIMEOUT_DEFAULT);
    }

    /**
     * Get the {@link Buffer} where events are stored when network is down.
     *
     * @param dsn Dsn passed in by the user.
     * @return the {@link Buffer} where events are stored when network is down.
     */
    protected Buffer getBuffer(Dsn dsn) {
        String bufferDir = dsn.getOptions().get(BUFFER_DIR_OPTION);
        if (bufferDir != null) {
            return new DiskBuffer(new File(bufferDir), getBufferSize(dsn));
        }
        return null;
    }

    /**
     * Get the maximum number of events to cache offline when network is down.
     *
     * @param dsn Dsn passed in by the user.
     * @return the maximum number of events to cache offline when network is down.
     */
    protected int getBufferSize(Dsn dsn) {
        return Util.parseInteger(dsn.getOptions().get(BUFFER_SIZE_OPTION), BUFFER_SIZE_DEFAULT);
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
            if (!t.isDaemon()) {
                t.setDaemon(true);
            }
            if (t.getPriority() != priority) {
                t.setPriority(priority);
            }
            return t;
        }
    }
}
