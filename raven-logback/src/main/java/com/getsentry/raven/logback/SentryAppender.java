package com.getsentry.raven.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import com.getsentry.raven.Raven;
import com.getsentry.raven.RavenFactory;
import com.getsentry.raven.config.Lookup;
import com.getsentry.raven.dsn.Dsn;
import com.getsentry.raven.dsn.InvalidDsnException;
import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.event.interfaces.ExceptionInterface;
import com.getsentry.raven.event.interfaces.MessageInterface;
import com.getsentry.raven.event.interfaces.SentryException;
import com.getsentry.raven.event.interfaces.StackTraceInterface;
import com.getsentry.raven.util.Util;

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
    public static final String THREAD_NAME = "Raven-Threadname";
    /**
     * Current instance of {@link Raven}.
     *
     * @see #initRaven()
     */
    protected volatile Raven raven;
    /**
     * DSN property of the appender.
     * <p>
     * Might be null in which case the DSN should be detected automatically.
     */
    protected String dsn;
    /**
     * Name of the {@link RavenFactory} being used.
     * <p>
     * Might be null in which case the factory should be defined automatically.
     */
    protected String ravenFactory;
    /**
     * Identifies the version of the application.
     * <p>
     * Might be null in which case the release information will not be sent with the event.
     */
    protected String release;
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
     * Creates an instance of SentryAppender.
     */
    public SentryAppender() {
        setRavenFactory(Lookup.lookup("ravenFactory"));
        setRelease(Lookup.lookup("release"));
        setEnvironment(Lookup.lookup("environment"));
        setServerName(Lookup.lookup("serverName"));
        setTags(Lookup.lookup("tags"));
        setExtraTags(Lookup.lookup("extraTags"));

        this.addFilter(new DropRavenFilter());
    }

    /**
     * Creates an instance of SentryAppender.
     *
     * @param raven instance of Raven to use with this appender.
     */
    public SentryAppender(Raven raven) {
        this();
        this.raven = raven;
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
     * @return log level used within raven.
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

    /**
     * {@inheritDoc}
     * <p>
     * The raven instance is started in this method instead of {@link #start()} in order to avoid substitute loggers
     * being generated during the instantiation of {@link Raven}.<br>
     * More on <a href="http://www.slf4j.org/codes.html#substituteLogger">www.slf4j.org/codes.html#substituteLogger</a>
     */
    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        // Do not log the event if the current thread is managed by raven
        if (RavenEnvironment.isManagingThread()) {
            return;
        }

        RavenEnvironment.startManagingThread();
        try {
            if (minLevel != null && !iLoggingEvent.getLevel().isGreaterOrEqual(minLevel)) {
                return;
            }

            if (raven == null) {
                initRaven();
            }
            Event event = buildEvent(iLoggingEvent);
            raven.sendEvent(event);
        } catch (Exception e) {
            addError("An exception occurred while creating a new event in Raven", e);
        } finally {
            RavenEnvironment.stopManagingThread();
        }
    }

    /**
     * Initialises the Raven instance.
     */
    protected synchronized void initRaven() {
        try {
            if (dsn == null) {
                dsn = Dsn.dsnLookup();
            }

            raven = RavenFactory.ravenInstance(new Dsn(dsn), ravenFactory);
        } catch (InvalidDsnException e) {
            addError("An exception occurred during the retrieval of the DSN for Raven", e);
        } catch (Exception e) {
            addError("An exception occurred during the creation of a Raven instance", e);
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
            .withSdkName(RavenEnvironment.SDK_NAME + ":logback")
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

        raven.runBuilderHelpers(eventBuilder);
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

    public void setRavenFactory(String ravenFactory) {
        this.ravenFactory = ravenFactory;
    }

    public void setRelease(String release) {
        this.release = release;
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
        RavenEnvironment.startManagingThread();
        try {
            if (!isStarted()) {
                return;
            }
            super.stop();
            if (raven != null) {
                raven.closeConnection();
            }
        } catch (Exception e) {
            addError("An exception occurred while closing the Raven connection", e);
        } finally {
            RavenEnvironment.stopManagingThread();
        }
    }

    private class DropRavenFilter extends Filter<ILoggingEvent> {
        @Override
        public FilterReply decide(ILoggingEvent event) {
            String loggerName = event.getLoggerName();
            if (loggerName != null && loggerName.startsWith("com.getsentry.raven")) {
                return FilterReply.DENY;
            }
            return FilterReply.NEUTRAL;
        }
    }
}
