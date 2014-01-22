package net.kencochrane.raven.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.dsn.Dsn;
import net.kencochrane.raven.dsn.InvalidDsnException;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.SentryException;
import net.kencochrane.raven.event.interfaces.MessageInterface;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

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

    private static final Logger logger = LoggerFactory.getLogger(SentryAppender.class);

    /**
     * Current instance of {@link Raven}.
     *
     * @see #initRaven()
     */
    protected Raven raven;
    /**
     * DSN property of the appender.
     * <p>
     * Might be null in which case the DSN should be detected automatically.
     * </p>
     */
    protected String dsn;
    /**
     * Name of the {@link RavenFactory} being used.
     * <p>
     * Might be null in which case the factory should be defined automatically.
     * </p>
     */
    protected String ravenFactory;
    /**
     * Additional tags to be sent to sentry.
     * <p>
     * Might be empty in which case no tags are sent.
     * </p>
     */
    protected Map<String, String> tags = Collections.emptyMap();

    /**
     * Creates an instance of SentryAppender.
     */
    public SentryAppender() {
    }

    /**
     * Creates an instance of SentryAppender.
     *
     * @param raven instance of Raven to use with this appender.
     */
    public SentryAppender(Raven raven) {
        this.raven = raven;
    }

    /**
     * Extracts message parameters into a List of Strings.
     * <p>
     * null parameters are kept as null.
     * </p>
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
        } else return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The raven instance is started in this method instead of {@link #start()} in order to avoid substitute loggers
     * being generated during the instantiation of {@link Raven}.<br />
     * More on <a href="http://www.slf4j.org/codes.html#substituteLogger">www.slf4j.org/codes.html#substituteLogger</a>
     * </p>
     */
    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        // Do not log the event if the current thread is managed by raven
        if (Raven.RAVEN_THREAD.get())
            return;

        try {
            Raven.RAVEN_THREAD.set(true);

            if (raven == null)
                initRaven();

            Event event = buildEvent(iLoggingEvent);
            raven.sendEvent(event);
        } catch (Exception e) {
            addError("An exception occurred while creating a new event in Raven", e);
        } finally {
            Raven.RAVEN_THREAD.remove();
        }
    }

    /**
     * Initialises the Raven instance.
     */
    protected void initRaven() {
        try {
            if (dsn == null)
                dsn = Dsn.dsnLookup();

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
                .setTimestamp(new Date(iLoggingEvent.getTimeStamp()))
                .setMessage(iLoggingEvent.getFormattedMessage())
                .setLogger(iLoggingEvent.getLoggerName())
                .setLevel(formatLevel(iLoggingEvent.getLevel()))
                .addExtra(THREAD_NAME, iLoggingEvent.getThreadName());

        if (iLoggingEvent.getArgumentArray() != null) {
            eventBuilder.addSentryInterface(new MessageInterface(iLoggingEvent.getMessage(),
                    formatMessageParameters(iLoggingEvent.getArgumentArray())));
        }

        if (iLoggingEvent.getThrowableProxy() != null) {
            eventBuilder.addSentryInterface(new ExceptionInterface(extractExceptionQueue(iLoggingEvent)));
        } else if (iLoggingEvent.getCallerData().length > 0) {
            eventBuilder.addSentryInterface(new StackTraceInterface(iLoggingEvent.getCallerData()));
        }

        if (iLoggingEvent.getCallerData().length > 0) {
            eventBuilder.setCulprit(iLoggingEvent.getCallerData()[0]);
        } else {
            eventBuilder.setCulprit(iLoggingEvent.getLoggerName());
        }

        for (Map.Entry<String, String> mdcEntry : iLoggingEvent.getMDCPropertyMap().entrySet()) {
            eventBuilder.addExtra(mdcEntry.getKey(), mdcEntry.getValue());
        }

        if (iLoggingEvent.getMarker() != null)
            eventBuilder.addTag(LOGBACK_MARKER, iLoggingEvent.getMarker().getName());

        for (Map.Entry<String, String> tagEntry : tags.entrySet())
            eventBuilder.addTag(tagEntry.getKey(), tagEntry.getValue());

        raven.runBuilderHelpers(eventBuilder);
        return eventBuilder.build();
    }

    private Deque<SentryException> extractExceptionQueue(final ILoggingEvent iLoggingEvent) {
        IThrowableProxy throwableProxy = iLoggingEvent.getThrowableProxy();
        Deque<SentryException> exceptions = new ArrayDeque<>();
        Set<IThrowableProxy> circularityDetector = new HashSet<>();
        StackTraceElement[] enclosingStackTrace = new StackTraceElement[0];

        //Stack the exceptions to send them in the reverse order
        while (throwableProxy != null) {
            if (!circularityDetector.add(throwableProxy)) {
                logger.warn("Exiting a circular exception!");
                break;
            }

            final StackTraceElement[] stackTraceElements = toStackTraceElements(throwableProxy);
            StackTraceInterface stackTrace = new StackTraceInterface(stackTraceElements, enclosingStackTrace);
            exceptions.push(createExceptionWithStackTraceFrom(throwableProxy, stackTrace));
            enclosingStackTrace = stackTraceElements;
            throwableProxy = throwableProxy.getCause();
        }

        return exceptions;
    }

    private SentryException createExceptionWithStackTraceFrom(final IThrowableProxy throwableProxy,
                                                                      final StackTraceInterface stackTrace) {
        final String exceptionMessage = throwableProxy.getMessage();
        final String exceptionClassName = throwableProxy.getClassName();
        final String exceptionPackageName = extractPackageName(throwableProxy);
        return new SentryException(exceptionMessage, exceptionClassName, exceptionPackageName, stackTrace);
    }

    private String extractPackageName(final IThrowableProxy throwableProxy) {

        // TODO this probably fails with application specific classes which are unknown to the logserver
        try {
            final Class<?> exceptionClass = Class.forName(throwableProxy.getClassName());
            final Package exceptionPackage = exceptionClass.getPackage();

            if (exceptionPackage != null) {
                return exceptionPackage.getName();
            }

        } catch (final ClassNotFoundException e) {
            logger.error("Could not load exception class", e);
        }

        return null;
    }

    private StackTraceElement[] toStackTraceElements(final IThrowableProxy throwableProxy) {
        final StackTraceElementProxy[] stackTraceElementProxies = throwableProxy.getStackTraceElementProxyArray();
        final List<StackTraceElement> stackTraceElements = Lists.newArrayList();

        for (final StackTraceElementProxy stackTraceElementProxy : stackTraceElementProxies) {
            stackTraceElements.add(stackTraceElementProxy.getStackTraceElement());
        }

        return stackTraceElements.toArray(new StackTraceElement[stackTraceElements.size()]);
    }

    public void setDsn(String dsn) {
        this.dsn = dsn;
    }

    public void setRavenFactory(String ravenFactory) {
        this.ravenFactory = ravenFactory;
    }

    /**
     * Set the tags that should be sent along with the events.
     *
     * @param tags A String of tags. key/values are separated by colon(:) and tags are separated by commas(,).
     */
    public void setTags(String tags) {
        this.tags = Splitter.on(",").withKeyValueSeparator(":").split(tags);
    }

    @Override
    public void stop() {
        super.stop();

        try {
            if (raven != null)
                raven.getConnection().close();
        } catch (IOException e) {
            addError("An exception occurred while closing the Raven connection", e);
        }
    }
}
