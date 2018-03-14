package io.sentry;

import io.sentry.buffer.Buffer;
import io.sentry.buffer.DiskBuffer;
import io.sentry.config.Lookup;
import io.sentry.connection.*;
import io.sentry.context.ContextManager;
import io.sentry.context.ThreadLocalContextManager;
import io.sentry.dsn.Dsn;
import io.sentry.event.helper.ContextBuilderHelper;
import io.sentry.event.helper.HttpEventBuilderHelper;
import io.sentry.event.interfaces.*;
import io.sentry.jvmti.FrameCache;
import io.sentry.marshaller.Marshaller;
import io.sentry.marshaller.json.*;
import io.sentry.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Authenticator;
import java.net.Proxy;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation of {@link SentryClientFactory}.
 * <p>
 * In most cases this is the implementation to use or extend for additional features.
 */
public class DefaultSentryClientFactory extends SentryClientFactory {
    /**
     * Protocol setting to disable security checks over an SSL connection.
     */
    public static final String NAIVE_PROTOCOL = "naive";
    /**
     * Option for whether to compress requests sent to the Sentry Server.
     */
    public static final String COMPRESSION_OPTION = "compression";
    /**
     * Option to set the maximum length of the message body in the requests to the
     * Sentry Server.
     */
    public static final String MAX_MESSAGE_LENGTH_OPTION = "maxmessagelength";
    /**
     * Option to set a timeout for requests to the Sentry server, in milliseconds.
     */
    public static final String TIMEOUT_OPTION = "timeout";
    /**
     * Default timeout of an HTTP connection to Sentry.
     */
    public static final int TIMEOUT_DEFAULT = (int) TimeUnit.SECONDS.toMillis(1);
    /**
     * Option to enable or disable Event buffering. A buffering directory is also required.
     * This setting is mostly useful on Android where a buffering directory is set by default.
     */
    public static final String BUFFER_ENABLED_OPTION = "buffer.enabled";
    /**
     * Default value for whether buffering is enabled (if a directory is also provided).
     */
    public static final boolean BUFFER_ENABLED_DEFAULT = true;
    /**
     * Option to buffer events to disk when network is down.
     */
    public static final String BUFFER_DIR_OPTION = "buffer.dir";
    /**
     * Option for maximum number of events to cache offline when network is down.
     */
    public static final String BUFFER_SIZE_OPTION = "buffer.size";
    /**
     * Default number of events to cache offline when network is down or project is throttled.
     */
    public static final int BUFFER_SIZE_DEFAULT = 10;
    /**
     * Option for how long to wait between attempts to flush the disk buffer, in milliseconds.
     */
    public static final String BUFFER_FLUSHTIME_OPTION = "buffer.flushtime";
    /**
     * Default number of milliseconds between attempts to flush buffered events.
     */
    public static final long BUFFER_FLUSHTIME_DEFAULT = 60000;
    /**
     * Option to disable the graceful shutdown of the buffer flusher.
     */
    public static final String BUFFER_GRACEFUL_SHUTDOWN_OPTION = "buffer.gracefulshutdown";
    /**
     * Option for the graceful shutdown timeout of the buffer flushing executor, in milliseconds.
     */
    public static final String BUFFER_SHUTDOWN_TIMEOUT_OPTION = "buffer.shutdowntimeout";
    /**
     * Default timeout of the {@link BufferedConnection} shutdown, in milliseconds.
     */
    public static final long BUFFER_SHUTDOWN_TIMEOUT_DEFAULT = TimeUnit.SECONDS.toMillis(1);
    /**
     * Option for whether to send events asynchronously.
     */
    public static final String ASYNC_OPTION = "async";
    /**
     * Option to disable the graceful shutdown of the async connection.
     */
    public static final String ASYNC_GRACEFUL_SHUTDOWN_OPTION = "async.gracefulshutdown";
    /**
     * Option for the number of threads used for the async connection.
     */
    public static final String ASYNC_THREADS_OPTION = "async.threads";
    /**
     * Option for the priority of threads used for the async connection.
     */
    public static final String ASYNC_PRIORITY_OPTION = "async.priority";
    /**
     * Option for the maximum size of the async send queue.
     */
    public static final String ASYNC_QUEUE_SIZE_OPTION = "async.queuesize";
    /**
     * Option for what to do when the async executor queue is full.
     */
    public static final String ASYNC_QUEUE_OVERFLOW_OPTION = "async.queue.overflow";
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
    public static final String ASYNC_SHUTDOWN_TIMEOUT_OPTION = "async.shutdowntimeout";
    /**
     * Default timeout of the {@link AsyncConnection} executor, in milliseconds.
     */
    public static final long ASYNC_SHUTDOWN_TIMEOUT_DEFAULT = TimeUnit.SECONDS.toMillis(1);
    /**
     * Option for which package prefixes are part of the user's application code, as a single
     * comma separated string.
     */
    public static final String IN_APP_FRAMES_OPTION = "stacktrace.app.packages";
    /**
     * Option for whether to hide common stackframes with enclosing exceptions.
     */
    public static final String HIDE_COMMON_FRAMES_OPTION = "stacktrace.hidecommon";
    /**
     * Option for whether to sample events, allowing from 0.0 to 1.0 (0 to 100%) to be sent to the server.
     */
    public static final String SAMPLE_RATE_OPTION = "sample.rate";
    /**
     * Option to set an HTTP proxy hostname for Sentry connections.
     */
    public static final String HTTP_PROXY_HOST_OPTION = "http.proxy.host";
    /**
     * Option to set an HTTP proxy port for Sentry connections.
     */
    public static final String HTTP_PROXY_PORT_OPTION = "http.proxy.port";
    /**
     * Option to set an HTTP proxy username for Sentry connections.
     */
    public static final String HTTP_PROXY_USER_OPTION = "http.proxy.user";
    /**
     * Option to set an HTTP proxy password for Sentry connections.
     */
    public static final String HTTP_PROXY_PASS_OPTION = "http.proxy.password";
    /**
     * The default async queue size if none is provided.
     */
    public static final int QUEUE_SIZE_DEFAULT = 50;
    /**
     * The default HTTP proxy port to use if an HTTP Proxy hostname is set but port is not.
     */
    public static final int HTTP_PROXY_PORT_DEFAULT = 80;
    /**
     * Option to set the version of the application.
     */
    public static final String RELEASE_OPTION = "release";
    /**
     * Option to set the distribution of the application.
     */
    public static final String DIST_OPTION = "dist";
    /**
     * Option to set the environment of the application.
     */
    public static final String ENVIRONMENT_OPTION = "environment";
    /**
     * Option to set the server name.
     */
    public static final String SERVERNAME_OPTION = "servername";
    /**
     * Option to set additional tags to be sent to Sentry.
     */
    public static final String TAGS_OPTION = "tags";
    /**
     * Option to set tags that are extracted from the MDC system, where applicable.
     * @deprecated prefer {@link DefaultSentryClientFactory#MDCTAGS_OPTION}
     */
    @Deprecated
    public static final String EXTRATAGS_OPTION = "extratags";
    /**
     * Option to set tags that are extracted from the MDC system, where applicable.
     */
    public static final String MDCTAGS_OPTION = "mdctags";
    /**
     * Option to set extra data to be sent to Sentry.
     */
    public static final String EXTRA_OPTION = "extra";
    /**
     * Option for whether to enable an uncaught exception handler, defaults to 'true'.
     */
    public static final String UNCAUGHT_HANDLER_ENABLED_OPTION = "uncaught.handler.enabled";

    private static final Logger logger = LoggerFactory.getLogger(DefaultSentryClientFactory.class);
    private static final String FALSE = Boolean.FALSE.toString();

    private static final Map<String, RejectedExecutionHandler> REJECT_EXECUTION_HANDLERS = new HashMap<>();
    static {
        REJECT_EXECUTION_HANDLERS.put(ASYNC_QUEUE_SYNC, new ThreadPoolExecutor.CallerRunsPolicy());
        REJECT_EXECUTION_HANDLERS.put(ASYNC_QUEUE_DISCARDNEW, new ThreadPoolExecutor.DiscardPolicy());
        REJECT_EXECUTION_HANDLERS.put(ASYNC_QUEUE_DISCARDOLD, new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    @Override
    public SentryClient createSentryClient(Dsn dsn) {
        try {
            SentryClient sentryClient = new SentryClient(createConnection(dsn), getContextManager(dsn));
            try {
                // `ServletRequestListener` was added in the Servlet 2.4 API, and
                // is used as part of the `HttpEventBuilderHelper`, see:
                // https://tomcat.apache.org/tomcat-5.5-doc/servletapi/
                Class.forName("javax.servlet.ServletRequestListener", false, this.getClass().getClassLoader());
                sentryClient.addBuilderHelper(new HttpEventBuilderHelper());
            } catch (ClassNotFoundException e) {
                logger.debug("The current environment doesn't provide access to servlets,"
                    + " or provides an unsupported version.");
            }
            sentryClient.addBuilderHelper(new ContextBuilderHelper(sentryClient));
            return configureSentryClient(sentryClient, dsn);
        } catch (Exception e) {
            logger.error("Failed to initialize sentry, falling back to no-op client", e);
            return new SentryClient(new NoopConnection(), new ThreadLocalContextManager());
        }
    }

    /**
     * Configures a {@link SentryClient} instance after it has been constructed.
     *
     * @param sentryClient The {@link SentryClient} to configure.
     * @param dsn Data Source Name of the Sentry server to use.
     * @return The same {@link SentryClient} instance, after configuration.
     */
    protected SentryClient configureSentryClient(SentryClient sentryClient, Dsn dsn) {
        String release = getRelease(dsn);
        if (release != null) {
            sentryClient.setRelease(release);
        }

        String dist = getDist(dsn);
        if (dist != null) {
            sentryClient.setDist(dist);
        }

        String environment = getEnvironment(dsn);
        if (environment != null) {
            sentryClient.setEnvironment(environment);
        }

        String serverName = getServerName(dsn);
        if (serverName != null) {
            sentryClient.setServerName(serverName);
        }

        Map<String, String> tags = getTags(dsn);
        if (!tags.isEmpty()) {
            for (Map.Entry<String, String> tagEntry : tags.entrySet()) {
                sentryClient.addTag(tagEntry.getKey(), tagEntry.getValue());
            }
        }

        Set<String> mdcTags = getMdcTags(dsn);
        if (!mdcTags.isEmpty()) {
            for (String mdcTag : mdcTags) {
                sentryClient.addMdcTag(mdcTag);
            }
        }

        Map<String, String> extra = getExtra(dsn);
        if (!extra.isEmpty()) {
            for (Map.Entry<String, String> extraEntry : extra.entrySet()) {
                sentryClient.addExtra(extraEntry.getKey(), extraEntry.getValue());
            }
        }

        if (getUncaughtHandlerEnabled(dsn)) {
            sentryClient.setupUncaughtExceptionHandler();
        }

        for (String inAppPackage : getInAppFrames(dsn)) {
            FrameCache.addAppPackage(inAppPackage);
        }

        return sentryClient;
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
            logger.debug("Using an {} connection to Sentry.", protocol.toUpperCase());
            connection = createHttpConnection(dsn);
        } else if (protocol.equalsIgnoreCase("out")) {
            logger.debug("Using StdOut to send events.");
            connection = createStdOutConnection(dsn);
        } else if (protocol.equalsIgnoreCase("noop")) {
            logger.debug("Using noop to send events.");
            connection = new NoopConnection();
        } else {
            throw new IllegalStateException("Couldn't create a connection for the protocol '" + protocol + "'");
        }

        BufferedConnection bufferedConnection = null;
        if (getBufferEnabled(dsn)) {
            Buffer eventBuffer = getBuffer(dsn);
            if (eventBuffer != null) {
                long flushtime = getBufferFlushtime(dsn);
                boolean gracefulShutdown = getBufferedConnectionGracefulShutdownEnabled(dsn);
                Long shutdownTimeout = getBufferedConnectionShutdownTimeout(dsn);
                bufferedConnection = new BufferedConnection(connection, eventBuffer, flushtime, gracefulShutdown,
                    shutdownTimeout);
                connection = bufferedConnection;
            }
        }

        // Enable async unless its value is 'false'.
        if (getAsyncEnabled(dsn)) {
            connection = createAsyncConnection(dsn, connection);
        }

        // If buffering is enabled, wrap connection with synchronous disk buffering "connection"
        if (bufferedConnection != null) {
            connection = bufferedConnection.wrapConnectionWithBufferWriter(connection);
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
        httpConnection.setConnectionTimeout(timeout);

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
     * Creates a JSON marshaller that will convert every {@link io.sentry.event.Event} in a format
     * handled by the Sentry server.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return a {@link JsonMarshaller} to process the events.
     */
    protected Marshaller createMarshaller(Dsn dsn) {
        int maxMessageLength = getMaxMessageLength(dsn);
        JsonMarshaller marshaller = createJsonMarshaller(maxMessageLength);

        // Set JSON marshaller bindings
        StackTraceInterfaceBinding stackTraceBinding = new StackTraceInterfaceBinding();
        // Enable common frames hiding unless its value is 'false'.
        stackTraceBinding.setRemoveCommonFramesWithEnclosing(getHideCommonFramesEnabled(dsn));
        stackTraceBinding.setInAppFrames(getInAppFrames(dsn));

        marshaller.addInterfaceBinding(StackTraceInterface.class, stackTraceBinding);
        marshaller.addInterfaceBinding(ExceptionInterface.class, new ExceptionInterfaceBinding(stackTraceBinding));
        marshaller.addInterfaceBinding(MessageInterface.class, new MessageInterfaceBinding(maxMessageLength));
        marshaller.addInterfaceBinding(UserInterface.class, new UserInterfaceBinding());
        marshaller.addInterfaceBinding(DebugMetaInterface.class, new DebugMetaInterfaceBinding());
        HttpInterfaceBinding httpBinding = new HttpInterfaceBinding();
        //TODO: Add a way to clean the HttpRequest
        //httpBinding.
        marshaller.addInterfaceBinding(HttpInterface.class, httpBinding);

        // Enable compression unless the option is set to false
        marshaller.setCompression(getCompressionEnabled(dsn));

        return marshaller;
    }

    /**
     * Create a {@link JsonMarshaller}. This method makes it easier to provide a custom implementation.
     *
     * @param maxMessageLength of the whole json output
     * @return new marshaller
     */
    @SuppressWarnings("WeakerAccess")
    protected JsonMarshaller createJsonMarshaller(int maxMessageLength) {
        return new JsonMarshaller(maxMessageLength);
    }

    /**
     * Returns the {@link ContextManager} to use for locating and storing data that is context specific,
     * such as {@link io.sentry.event.Breadcrumb}s.
     * <p>
     * Defaults to {@link ThreadLocalContextManager}.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return the {@link ContextManager} to use.
     */
    protected ContextManager getContextManager(Dsn dsn) {
        return new ThreadLocalContextManager();
    }

    /**
     * Returns the list of package names to consider "in-app".
     * <p>
     * Those packages will be used with the {@link StackTraceInterface} to show frames that are a part of
     * the main application in the Sentry UI by default.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return the list of package names to consider "in-app".
     */
    protected Collection<String> getInAppFrames(Dsn dsn) {
        String inAppFramesOption = Lookup.lookup(IN_APP_FRAMES_OPTION, dsn);
        if (Util.isNullOrEmpty(inAppFramesOption)) {
            // Only warn if the user didn't set it at all
            if (inAppFramesOption == null) {
                logger.warn("No '" + IN_APP_FRAMES_OPTION + "' was configured, this option is highly recommended "
                    + "as it affects stacktrace grouping and display on Sentry. See documentation: "
                    + "https://docs.sentry.io/clients/java/config/#in-application-stack-frames");
            }
            return Collections.emptyList();
        }

        List<String> inAppPackages = new ArrayList<>();
        for (String inAppPackage : inAppFramesOption.split(",")) {
            if (!inAppPackage.trim().equals("")) {
                inAppPackages.add(inAppPackage);
            }
        }

        return inAppPackages;
    }

    /**
     * Whether or not to wrap the underlying connection in an {@link AsyncConnection}.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Whether or not to wrap the underlying connection in an {@link AsyncConnection}.
     */
    protected boolean getAsyncEnabled(Dsn dsn) {
        return !FALSE.equalsIgnoreCase(Lookup.lookup(ASYNC_OPTION, dsn));
    }

    /**
     * Handler for tasks that cannot be immediately queued by a {@link ThreadPoolExecutor}.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Handler for tasks that cannot be immediately queued by a {@link ThreadPoolExecutor}.
     */
    protected RejectedExecutionHandler getRejectedExecutionHandler(Dsn dsn) {
        String overflowName = ASYNC_QUEUE_OVERFLOW_DEFAULT;
        String asyncQueueOverflowOption = Lookup.lookup(ASYNC_QUEUE_OVERFLOW_OPTION, dsn);
        if (!Util.isNullOrEmpty(asyncQueueOverflowOption)) {
            overflowName = asyncQueueOverflowOption.toLowerCase();
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
        return Util.parseLong(Lookup.lookup(BUFFER_SHUTDOWN_TIMEOUT_OPTION, dsn), BUFFER_SHUTDOWN_TIMEOUT_DEFAULT);
    }

    /**
     * Whether or not to attempt a graceful shutdown of the {@link BufferedConnection} upon close.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Whether or not to attempt a graceful shutdown of the {@link BufferedConnection} upon close.
     */
    protected boolean getBufferedConnectionGracefulShutdownEnabled(Dsn dsn) {
        return !FALSE.equalsIgnoreCase(Lookup.lookup(BUFFER_GRACEFUL_SHUTDOWN_OPTION, dsn));
    }

    /**
     * How long to wait between attempts to flush the disk buffer, in milliseconds.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return ow long to wait between attempts to flush the disk buffer, in milliseconds.
     */
    protected long getBufferFlushtime(Dsn dsn) {
        return Util.parseLong(Lookup.lookup(BUFFER_FLUSHTIME_OPTION, dsn), BUFFER_FLUSHTIME_DEFAULT);
    }

    /**
     * The graceful shutdown timeout of the async executor, in milliseconds.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return The graceful shutdown timeout of the async executor, in milliseconds.
     */
    protected long getAsyncShutdownTimeout(Dsn dsn) {
        return Util.parseLong(Lookup.lookup(ASYNC_SHUTDOWN_TIMEOUT_OPTION, dsn), ASYNC_SHUTDOWN_TIMEOUT_DEFAULT);
    }

    /**
     * Whether or not to attempt the graceful shutdown of the {@link AsyncConnection} upon close.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Whether or not to attempt the graceful shutdown of the {@link AsyncConnection} upon close.
     */
    protected boolean getAsyncGracefulShutdownEnabled(Dsn dsn) {
        return !FALSE.equalsIgnoreCase(Lookup.lookup(ASYNC_GRACEFUL_SHUTDOWN_OPTION, dsn));
    }

    /**
     * Maximum size of the async send queue.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Maximum size of the async send queue.
     */
    protected int getAsyncQueueSize(Dsn dsn) {
        return Util.parseInteger(Lookup.lookup(ASYNC_QUEUE_SIZE_OPTION, dsn), QUEUE_SIZE_DEFAULT);
    }

    /**
     * Priority of threads used for the async connection.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Priority of threads used for the async connection.
     */
    protected int getAsyncPriority(Dsn dsn) {
        return Util.parseInteger(Lookup.lookup(ASYNC_PRIORITY_OPTION, dsn), Thread.MIN_PRIORITY);
    }

    /**
     * The number of threads used for the async connection.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return The number of threads used for the async connection.
     */
    protected int getAsyncThreads(Dsn dsn) {
        return Util.parseInteger(Lookup.lookup(ASYNC_THREADS_OPTION, dsn),
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
        return Util.parseDouble(Lookup.lookup(SAMPLE_RATE_OPTION, dsn), null);
    }

    /**
     * HTTP proxy port for Sentry connections.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return HTTP proxy port for Sentry connections.
     */
    protected int getProxyPort(Dsn dsn) {
        return Util.parseInteger(Lookup.lookup(HTTP_PROXY_PORT_OPTION, dsn), HTTP_PROXY_PORT_DEFAULT);
    }

    /**
     * HTTP proxy hostname for Sentry connections.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return HTTP proxy hostname for Sentry connections.
     */
    protected String getProxyHost(Dsn dsn) {
        return Lookup.lookup(HTTP_PROXY_HOST_OPTION, dsn);
    }

    /**
     * HTTP proxy username for Sentry connections.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return HTTP proxy username for Sentry connections.
     */
    protected String getProxyUser(Dsn dsn) {
        return Lookup.lookup(HTTP_PROXY_USER_OPTION, dsn);
    }

    /**
     * HTTP proxy password for Sentry connections.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return HTTP proxy password for Sentry connections.
     */
    protected String getProxyPass(Dsn dsn) {
        return Lookup.lookup(HTTP_PROXY_PASS_OPTION, dsn);
    }

    /**
     * Application version to send with {@link io.sentry.event.Event}s that don't already
     * have a value for the field set.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Application version to send with {@link io.sentry.event.Event}s.
     */
    protected String getRelease(Dsn dsn) {
        return Lookup.lookup(RELEASE_OPTION, dsn);
    }

    /**
     * Application distribution to send with {@link io.sentry.event.Event}s that don't already
     * have a value for the field set.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Application version to send with {@link io.sentry.event.Event}s.
     */
    protected String getDist(Dsn dsn) {
        return Lookup.lookup(DIST_OPTION, dsn);
    }

    /**
     * Application environmentribution to send with {@link io.sentry.event.Event}s that don't already
     * have a value for the field set.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Application version to send with {@link io.sentry.event.Event}s.
     */
    protected String getEnvironment(Dsn dsn) {
        return Lookup.lookup(ENVIRONMENT_OPTION, dsn);
    }

    /**
     * Server name to send with {@link io.sentry.event.Event}s that don't already
     * have a value for the field set.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Server name to send with {@link io.sentry.event.Event}s.
     */
    protected String getServerName(Dsn dsn) {
        return Lookup.lookup(SERVERNAME_OPTION, dsn);
    }

    /**
     * Additional tags to send with {@link io.sentry.event.Event}s.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Additional tags to send with {@link io.sentry.event.Event}s.
     */
    protected Map<String, String> getTags(Dsn dsn) {
        return Util.parseTags(Lookup.lookup(TAGS_OPTION, dsn));
    }

    /**
     * Tags to extract from the MDC system and set on {@link io.sentry.event.Event}s, where applicable.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Tags to extract from the MDC system and set on {@link io.sentry.event.Event}s, where applicable.
     * @deprecated prefer {@link DefaultSentryClientFactory#getMdcTags(Dsn)}
     */
    @Deprecated
    protected Set<String> getExtraTags(Dsn dsn) {
        return getMdcTags(dsn);
    }

    /**
     * Tags to extract from the MDC system and set on {@link io.sentry.event.Event}s, where applicable.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Tags to extract from the MDC system and set on {@link io.sentry.event.Event}s, where applicable.
     */
    protected Set<String> getMdcTags(Dsn dsn) {
        String val = Lookup.lookup(MDCTAGS_OPTION, dsn);
        if (Util.isNullOrEmpty(val)) {
            val = Lookup.lookup(EXTRATAGS_OPTION, dsn);
            if (!Util.isNullOrEmpty(val)) {
                logger.warn("The '" + EXTRATAGS_OPTION + "' option is deprecated, please use"
                    + " the '" + MDCTAGS_OPTION + "' option instead.");
            }
        }

        return Util.parseMdcTags(val);
    }

    /**
     * Extra data to send with {@link io.sentry.event.Event}s.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Extra data to send with {@link io.sentry.event.Event}s.
     */
    protected Map<String, String> getExtra(Dsn dsn) {
        return Util.parseExtra(Lookup.lookup(EXTRA_OPTION, dsn));
    }

    /**
     * Whether to compress requests sent to the Sentry Server.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Whether to compress requests sent to the Sentry Server.
     */
    protected boolean getCompressionEnabled(Dsn dsn) {
        return !FALSE.equalsIgnoreCase(Lookup.lookup(COMPRESSION_OPTION, dsn));
    }

    /**
     * Whether to hide common stackframes with enclosing exceptions.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Whether to hide common stackframes with enclosing exceptions.
     */
    protected boolean getHideCommonFramesEnabled(Dsn dsn) {
        return !FALSE.equalsIgnoreCase(Lookup.lookup(HIDE_COMMON_FRAMES_OPTION, dsn));
    }

    /**
     * The maximum length of the message body in the requests to the Sentry Server.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return The maximum length of the message body in the requests to the Sentry Server.
     */
    protected int getMaxMessageLength(Dsn dsn) {
        return Util.parseInteger(
            Lookup.lookup(MAX_MESSAGE_LENGTH_OPTION, dsn), JsonMarshaller.DEFAULT_MAX_MESSAGE_LENGTH);
    }

    /**
     * Timeout for requests to the Sentry server, in milliseconds.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Timeout for requests to the Sentry server, in milliseconds.
     */
    protected int getTimeout(Dsn dsn) {
        return Util.parseInteger(Lookup.lookup(TIMEOUT_OPTION, dsn), TIMEOUT_DEFAULT);
    }

    /**
     * Whether or not buffering is enabled.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Whether or not buffering is enabled.
     */
    protected boolean getBufferEnabled(Dsn dsn) {
        String bufferEnabled = Lookup.lookup(BUFFER_ENABLED_OPTION, dsn);
        if (bufferEnabled != null) {
            return Boolean.parseBoolean(bufferEnabled);
        }
        return BUFFER_ENABLED_DEFAULT;
    }

    /**
     * Get the {@link Buffer} where events are stored when network is down.
     *
     * @param dsn Dsn passed in by the user.
     * @return the {@link Buffer} where events are stored when network is down.
     */
    protected Buffer getBuffer(Dsn dsn) {
        String bufferDir = Lookup.lookup(BUFFER_DIR_OPTION, dsn);
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
        return Util.parseInteger(Lookup.lookup(BUFFER_SIZE_OPTION, dsn), BUFFER_SIZE_DEFAULT);
    }

    /**
     * Whether or not to enable a {@link SentryUncaughtExceptionHandler}.
     *
     * @param dsn Sentry server DSN which may contain options.
     * @return Whether or not to enable a {@link SentryUncaughtExceptionHandler}.
     */
    protected boolean getUncaughtHandlerEnabled(Dsn dsn) {
        return !FALSE.equalsIgnoreCase(Lookup.lookup(UNCAUGHT_HANDLER_ENABLED_OPTION, dsn));
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
            namePrefix = "sentry-pool-" + POOL_NUMBER.getAndIncrement() + "-thread-";
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
