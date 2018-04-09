package io.sentry.jul;

import io.sentry.Sentry;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.MessageInterface;
import org.slf4j.MDC;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.*;

/**
 * Logging handler in charge of sending the java.util.logging records to a Sentry server.
 */
public class SentryHandler extends Handler {
    /**
     * Name of the {@link Event#extra} property containing the Thread id.
     */
    public static final String THREAD_ID = "Sentry-ThreadId";
    /**
     * If true, <code>String.format()</code> is used to render parameterized log
     * messages instead of <code>MessageFormat.format()</code>; Defaults to
     * false.
     */
    protected boolean printfStyle;

    /**
     * Creates an instance of SentryHandler.
     */
    public SentryHandler() {
        retrieveProperties();
        this.setFilter(new DropSentryFilter());
    }

    /**
     * Transforms a {@link Level} into an {@link Event.Level}.
     *
     * @param level original level as defined in JUL.
     * @return log level used within sentry.
     */
    protected static Event.Level getLevel(Level level) {
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return Event.Level.ERROR;
        } else if (level.intValue() >= Level.WARNING.intValue()) {
            return Event.Level.WARNING;
        } else if (level.intValue() >= Level.INFO.intValue()) {
            return Event.Level.INFO;
        } else if (level.intValue() >= Level.ALL.intValue()) {
            return Event.Level.DEBUG;
        } else {
            return null;
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
        List<String> formattedParameters = new ArrayList<>(parameters.length);
        for (Object parameter : parameters) {
            formattedParameters.add((parameter != null) ? parameter.toString() : null);
        }
        return formattedParameters;
    }

    /**
     * Retrieves the properties of the logger.
     */
    protected void retrieveProperties() {
        LogManager manager = LogManager.getLogManager();
        String className = SentryHandler.class.getName();
        setPrintfStyle(Boolean.valueOf(manager.getProperty(className + ".printfStyle")));
        setLevel(parseLevelOrDefault(manager.getProperty(className + ".level")));
    }

    private Level parseLevelOrDefault(String levelName) {
        try {
            return Level.parse(levelName.trim());
        } catch (Exception e) {
            return Level.WARNING;
        }
    }

    @Override
    public void publish(LogRecord record) {
        // Do not log the event if the current thread is managed by sentry
        if (!isLoggable(record) || SentryEnvironment.isManagingThread()) {
            return;
        }

        SentryEnvironment.startManagingThread();
        try {
            EventBuilder eventBuilder = createEventBuilder(record);
            Sentry.capture(eventBuilder);
        } catch (Exception e) {
            reportError("An exception occurred while creating a new event in Sentry", e, ErrorManager.WRITE_FAILURE);
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }

    /**
     * Builds an EventBuilder based on the log record.
     *
     * @param record Log generated.
     * @return EventBuilder containing details provided by the logging system.
     */
    protected EventBuilder createEventBuilder(LogRecord record) {
        EventBuilder eventBuilder = new EventBuilder()
            .withSdkIntegration("java.util.logging")
            .withLevel(getLevel(record.getLevel()))
            .withTimestamp(new Date(record.getMillis()))
            .withLogger(record.getLoggerName());

        String message = record.getMessage();
        if (record.getResourceBundle() != null && record.getResourceBundle().containsKey(record.getMessage())) {
            message = record.getResourceBundle().getString(record.getMessage());
        }

        String topLevelMessage = message;
        if (record.getParameters() == null) {
            eventBuilder.withSentryInterface(new MessageInterface(message));
        } else {
            String formatted;
            List<String> parameters = formatMessageParameters(record.getParameters());
            try {
                formatted = formatMessage(message, record.getParameters());
                topLevelMessage = formatted; // write out formatted as Event's message key
            } catch (Exception e) {
                // local formatting failed, send message and parameters without formatted string
                formatted = null;
            }
            eventBuilder.withSentryInterface(new MessageInterface(message, parameters, formatted));
        }
        eventBuilder.withMessage(topLevelMessage);

        Throwable throwable = record.getThrown();
        if (throwable != null) {
            eventBuilder.withSentryInterface(new ExceptionInterface(throwable));
        }

        Map<String, String> mdc = MDC.getMDCAdapter().getCopyOfContextMap();
        if (mdc != null) {
            for (Map.Entry<String, String> mdcEntry : mdc.entrySet()) {
                if (Sentry.getStoredClient().getMdcTags().contains(mdcEntry.getKey())) {
                    eventBuilder.withTag(mdcEntry.getKey(), mdcEntry.getValue());
                } else {
                    eventBuilder.withExtra(mdcEntry.getKey(), mdcEntry.getValue());
                }
            }
        }

        eventBuilder.withExtra(THREAD_ID, record.getThreadID());

        return eventBuilder;
    }

    /**
     * Returns formatted Event message when provided the message template and
     * parameters.
     *
     * @param message Message template body.
     * @param parameters Array of parameters for the message.
     * @return Formatted message.
     */
    protected String formatMessage(String message, Object[] parameters) {
        String formatted;
        if (printfStyle) {
            formatted = String.format(message, parameters);
        } else {
            formatted = MessageFormat.format(message, parameters);
        }
        return formatted;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
        SentryEnvironment.startManagingThread();
        try {
            Sentry.close();
        } catch (Exception e) {
            reportError("An exception occurred while closing the Sentry connection", e, ErrorManager.CLOSE_FAILURE);
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }

    public void setPrintfStyle(boolean printfStyle) {
        this.printfStyle = printfStyle;
    }

    private class DropSentryFilter implements Filter {
        @Override
        public boolean isLoggable(LogRecord record) {
            String loggerName = record.getLoggerName();
            return loggerName == null || !loggerName.startsWith("io.sentry");
        }
    }
}
