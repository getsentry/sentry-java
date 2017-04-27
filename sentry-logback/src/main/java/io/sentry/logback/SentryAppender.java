package io.sentry.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import io.sentry.SentryClient;
import io.sentry.SentryClientFactory;
import io.sentry.config.Lookup;
import io.sentry.dsn.Dsn;
import io.sentry.dsn.InvalidDsnException;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.MessageInterface;
import io.sentry.event.interfaces.SentryException;
import io.sentry.event.interfaces.StackTraceInterface;
import io.sentry.util.Util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Appender for logback in charge of sending the logged events to a Sentry server.
 */
public class SentryAppender extends AppenderBase<ILoggingEvent> {

    /**
     * Name of the {@link Event#extra} property containing Maker details.
     */
    public static final String LOGBACK_MARKER = "logback-Marker";
    /**
     * Name of the {@link Event#extra} property containing the Thread name.
     */
    public static final String THREAD_NAME = "Sentry-Threadname";
    /**
     * Current instance of {@link SentryClient}.
     *
     * @see #initSentry()
     */
    protected volatile SentryClient sentryClient;
    /**
     * DSN property of the appender.
     * <p>
     * Might be null in which case the DSN should be detected automatically.
     */
    protected String dsn;
    /**
     * Name of the {@link SentryClientFactory} being used.
     * <p>
     * Might be null in which case the factory should be defined automatically.
     */
    protected String sentryClientFactory;
    /**
     * Identifies the version of the application.
     * <p>
     * Might be null in which case the release information will not be sent with the event.
     */
    protected String release;
    /**
     * Identifies the distribution of the application.
     * <p>
     * Might be null in which case the release distribution will not be sent with the event.
     */
    protected String dist;
    /**
     * Identifies the environment the application is running in.
     * <p>
     * Might be null in which case the environment information will not be sent with the event.
     */
    protected String environment;
    /**
     * Server name to be sent to sentry.
     * <p>
     * Might be null in which case the hostname is found via a reverse DNS lookup.
     */
    protected String serverName;
    /**
     * If set, only events with level = minLevel and up will be recorded. (This
     * configuration parameter is deprecated in favor of using Logback
     * Filters.)
     */
    protected Level minLevel;
    /**
     * Additional tags to be sent to sentry.
     * <p>
     * Might be empty in which case no tags are sent.
     */
    protected Map<String, String> tags = Collections.emptyMap();
    /**
     * Extras to use as tags.
     */
    protected Set<String> extraTags = Collections.emptySet();
    /**
     * Used for lazy initialization of appender state, see {@link #lazyInit()}.
     */
    private volatile boolean initialized = false;

    /**
     * Creates an instance of SentryAppender.
     */
    public SentryAppender() {
        this(null);
    }

    /**
     * Creates an instance of SentryAppender.
     *
     * @param sentryClient instance of Sentry to use with this appender.
     */
    public SentryAppender(SentryClient sentryClient) {
        this.addFilter(new DropSentryFilter());
        if (sentryClient != null) {
            this.sentryClient = sentryClient;
        } else {
            initSentry();
        }

    }

    /**
     * Do some appender initialization *after* instance construction, so that we don't
     * log in the constructor (which can cause annoying messages) and so that system
     * properties and environment variables override hardcoded appender configuration.
     */
    @SuppressWarnings("checkstyle:hiddenfield")
    private void lazyInit() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    try {
                        String release = Lookup.lookup("release");
                        if (release != null) {
                            setRelease(release);
                        }

                        String dist = Lookup.lookup("dist");
                        if (dist != null) {
                            setDist(dist);
                        }

                        String environment = Lookup.lookup("environment");
                        if (environment != null) {
                            setEnvironment(environment);
                        }

                        String serverName = Lookup.lookup("serverName");
                        if (serverName != null) {
                            setServerName(serverName);
                        }

                        String tags = Lookup.lookup("tags");
                        if (tags != null) {
                            setTags(tags);
                        }

                        String extraTags = Lookup.lookup("extraTags");
                        if (extraTags != null) {
                            setExtraTags(extraTags);
                        }
                    } finally {
                        initialized = true;
                    }
                }
            }
        }
    }

    /**
     * Extracts message parameters into a List of Strings.
     * <p>
     * null parameters are kept as null.
     *
     * @param parameters parameters provided to the logging system.
     * @return the parameters formatted as Strings in a List.
     */
    protected static List<String> formatMessageParameters(Object[] parameters) {
        List<String> arguments = new ArrayList<>(parameters.length);
        for (Object argument : parameters) {
            arguments.add((argument != null) ? argument.toString() : null);
        }
        return arguments;
    }

    /**
     * Transforms a {@link Level} into an {@link Event.Level}.
     *
     * @param level original level as defined in logback.
     * @return log level used within sentry.
     */
    protected static Event.Level formatLevel(Level level) {
        if (level.isGreaterOrEqual(Level.ERROR)) {
            return Event.Level.ERROR;
        } else if (level.isGreaterOrEqual(Level.WARN)) {
            return Event.Level.WARNING;
        } else if (level.isGreaterOrEqual(Level.INFO)) {
            return Event.Level.INFO;
        } else if (level.isGreaterOrEqual(Level.ALL)) {
            return Event.Level.DEBUG;
        } else {
            return null;
        }
    }

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        // Do not log the event if the current thread is managed by sentry
        if (SentryEnvironment.isManagingThread()) {
            return;
        }

        SentryEnvironment.startManagingThread();
        try {
            if (minLevel != null && !iLoggingEvent.getLevel().isGreaterOrEqual(minLevel)) {
                return;
            }

            lazyInit();
            Event event = buildEvent(iLoggingEvent);
            sentryClient.sendEvent(event);
        } catch (Exception e) {
            addError("An exception occurred while creating a new event in Sentry", e);
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }

    /**
     * Initialises the {@link SentryClient} instance.
     */
    protected void initSentry() {
        try {
            if (dsn == null) {
                dsn = Dsn.dsnLookup();
            }

            String factory = Lookup.lookup("factory");
            if (factory != null) {
                setFactory(factory);
            }

            sentryClient = SentryClientFactory.sentryClient(new Dsn(dsn), sentryClientFactory);
        } catch (InvalidDsnException e) {
            addError("An exception occurred during the retrieval of the DSN for Sentry", e);
        } catch (Exception e) {
            addError("An exception occurred during the creation of a SentryClient instance", e);
        }
    }

    /**
     * Builds an Event based on the logging event.
     *
     * @param iLoggingEvent Log generated.
     * @return Event containing details provided by the logging system.
     */
    protected Event buildEvent(ILoggingEvent iLoggingEvent) {
        EventBuilder eventBuilder = new EventBuilder()
            .withSdkName(SentryEnvironment.SDK_NAME + ":logback")
            .withTimestamp(new Date(iLoggingEvent.getTimeStamp()))
            .withMessage(iLoggingEvent.getFormattedMessage())
            .withLogger(iLoggingEvent.getLoggerName())
            .withLevel(formatLevel(iLoggingEvent.getLevel()))
            .withExtra(THREAD_NAME, iLoggingEvent.getThreadName());


        if (!Util.isNullOrEmpty(serverName)) {
            eventBuilder.withServerName(serverName.trim());
        }

        if (!Util.isNullOrEmpty(release)) {
            eventBuilder.withRelease(release.trim());
            if (!Util.isNullOrEmpty(dist)) {
                eventBuilder.withDist(dist.trim());
            }
        }

        if (!Util.isNullOrEmpty(environment)) {
            eventBuilder.withEnvironment(environment.trim());
        }

        if (iLoggingEvent.getArgumentArray() != null) {
            eventBuilder.withSentryInterface(new MessageInterface(
                iLoggingEvent.getMessage(),
                formatMessageParameters(iLoggingEvent.getArgumentArray()),
                iLoggingEvent.getFormattedMessage()));
        }

        if (iLoggingEvent.getThrowableProxy() != null) {
            eventBuilder.withSentryInterface(new ExceptionInterface(extractExceptionQueue(iLoggingEvent)));
        } else if (iLoggingEvent.getCallerData().length > 0) {
            eventBuilder.withSentryInterface(new StackTraceInterface(iLoggingEvent.getCallerData()));
        }

        if (iLoggingEvent.getCallerData().length > 0) {
            eventBuilder.withCulprit(iLoggingEvent.getCallerData()[0]);
        } else {
            eventBuilder.withCulprit(iLoggingEvent.getLoggerName());
        }

        for (Map.Entry<String, String> contextEntry : iLoggingEvent.getLoggerContextVO().getPropertyMap().entrySet()) {
            eventBuilder.withExtra(contextEntry.getKey(), contextEntry.getValue());
        }

        for (Map.Entry<String, String> mdcEntry : iLoggingEvent.getMDCPropertyMap().entrySet()) {
            if (extraTags.contains(mdcEntry.getKey())) {
                eventBuilder.withTag(mdcEntry.getKey(), mdcEntry.getValue());
            } else {
                eventBuilder.withExtra(mdcEntry.getKey(), mdcEntry.getValue());
            }
        }

        if (iLoggingEvent.getMarker() != null) {
            eventBuilder.withTag(LOGBACK_MARKER, iLoggingEvent.getMarker().getName());
        }

        for (Map.Entry<String, String> tagEntry : tags.entrySet()) {
            eventBuilder.withTag(tagEntry.getKey(), tagEntry.getValue());
        }

        sentryClient.runBuilderHelpers(eventBuilder);
        return eventBuilder.build();
    }

    /**
     * Creates a sequence of {@link SentryException}s given a particular {@link ILoggingEvent}.
     *
     * @param iLoggingEvent Information detailing a particular logging event
     *
     * @return A {@link Deque} of {@link SentryException}s detailing the exception chain
     */
    protected Deque<SentryException> extractExceptionQueue(ILoggingEvent iLoggingEvent) {
        IThrowableProxy throwableProxy = iLoggingEvent.getThrowableProxy();
        Deque<SentryException> exceptions = new ArrayDeque<>();
        Set<IThrowableProxy> circularityDetector = new HashSet<>();
        StackTraceElement[] enclosingStackTrace = new StackTraceElement[0];

        //Stack the exceptions to send them in the reverse order
        while (throwableProxy != null) {
            if (!circularityDetector.add(throwableProxy)) {
                addWarn("Exiting a circular exception!");
                break;
            }

            StackTraceElement[] stackTraceElements = toStackTraceElements(throwableProxy);
            StackTraceInterface stackTrace = new StackTraceInterface(stackTraceElements, enclosingStackTrace);
            exceptions.push(createSentryExceptionFrom(throwableProxy, stackTrace));
            enclosingStackTrace = stackTraceElements;
            throwableProxy = throwableProxy.getCause();
        }

        return exceptions;
    }

    /**
     * Given a {@link IThrowableProxy} and a {@link StackTraceInterface} return
     * a {@link SentryException} to be reported to Sentry.
     *
     * @param throwableProxy Information detailing a Throwable
     * @param stackTrace The stacktrace associated with the Throwable.
     *
     * @return A {@link SentryException} object ready to be sent to Sentry.
     */
    protected SentryException createSentryExceptionFrom(IThrowableProxy throwableProxy,
                                                        StackTraceInterface stackTrace) {
        String exceptionMessage = throwableProxy.getMessage();
        String[] packageNameSimpleName = extractPackageSimpleClassName(throwableProxy.getClassName());
        String exceptionPackageName = packageNameSimpleName[0];
        String exceptionClassName = packageNameSimpleName[1];

        return new SentryException(exceptionMessage, exceptionClassName, exceptionPackageName, stackTrace);
    }

    /**
     * Given a {@link String} representing a classname, return Strings
     * representing the package name and the class name individually.
     *
     * @param canonicalClassName A dotted-notation string representing a class name (eg. "java.util.Date")
     *
     * @return An array of {@link String}s. The first of which is the package name. The second is the class name.
     */
    protected String[] extractPackageSimpleClassName(String canonicalClassName) {
        String[] packageNameSimpleName = new String[2];
        try {
            Class<?> exceptionClass = Class.forName(canonicalClassName);
            Package exceptionPackage = exceptionClass.getPackage();
            packageNameSimpleName[0] = exceptionPackage != null ? exceptionPackage.getName()
                    : SentryException.DEFAULT_PACKAGE_NAME;
            packageNameSimpleName[1] = exceptionClass.getSimpleName();
        } catch (ClassNotFoundException e) {
            int lastDot = canonicalClassName.lastIndexOf('.');
            if (lastDot != -1) {
                packageNameSimpleName[0] = canonicalClassName.substring(0, lastDot);
                packageNameSimpleName[1] = canonicalClassName.substring(lastDot);
            } else {
                packageNameSimpleName[0] = SentryException.DEFAULT_PACKAGE_NAME;
                packageNameSimpleName[1] = canonicalClassName;
            }
        }
        return packageNameSimpleName;
    }

    /**
     * Given a {@link IThrowableProxy} return an array of {@link StackTraceElement}s
     * associated with the underlying {@link Throwable}.
     *
     * @param throwableProxy Information detailing a Throwable.
     *
     * @return The {@link StackTraceElement}s associated w/the underlying {@link Throwable}
     */
    protected StackTraceElement[] toStackTraceElements(IThrowableProxy throwableProxy) {
        StackTraceElementProxy[] stackTraceElementProxies = throwableProxy.getStackTraceElementProxyArray();
        StackTraceElement[] stackTraceElements = new StackTraceElement[stackTraceElementProxies.length];

        for (int i = 0, stackTraceElementsLength = stackTraceElementProxies.length; i < stackTraceElementsLength; i++) {
            stackTraceElements[i] = stackTraceElementProxies[i].getStackTraceElement();
        }

        return stackTraceElements;
    }

    public void setDsn(String dsn) {
        this.dsn = dsn;
    }

    public void setFactory(String factory) {
        this.sentryClientFactory = factory;
    }

    public void setRelease(String release) {
        this.release = release;
    }

    public void setDist(String dist) {
        this.dist = dist;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public void setMinLevel(String minLevel) {
        this.minLevel = minLevel != null ? Level.toLevel(minLevel) : null;
    }

    /**
     * Set the tags that should be sent along with the events.
     *
     * @param tags A String of tags. key/values are separated by colon(:) and tags are separated by commas(,).
     */
    public void setTags(String tags) {
        this.tags = Util.parseTags(tags);
    }

    /**
     * Set the mapped extras that will be used to search MDC and upgrade key pair to a tag sent along with the events.
     *
     * @param extraTags A String of extraTags. extraTags are separated by commas(,).
     */
    public void setExtraTags(String extraTags) {
        this.extraTags = Util.parseExtraTags(extraTags);
    }

    @Override
    public void stop() {
        SentryEnvironment.startManagingThread();
        try {
            if (!isStarted()) {
                return;
            }
            super.stop();
            if (sentryClient != null) {
                sentryClient.closeConnection();
            }
        } catch (Exception e) {
            addError("An exception occurred while closing the Sentry connection", e);
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }

    private class DropSentryFilter extends Filter<ILoggingEvent> {
        @Override
        public FilterReply decide(ILoggingEvent event) {
            String loggerName = event.getLoggerName();
            if (loggerName != null && loggerName.startsWith("io.sentry")) {
                return FilterReply.DENY;
            }
            return FilterReply.NEUTRAL;
        }
    }
}
