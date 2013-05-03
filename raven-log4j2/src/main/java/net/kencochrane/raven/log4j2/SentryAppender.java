package net.kencochrane.raven.log4j2;

import net.kencochrane.raven.Dsn;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.MessageInterface;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttr;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Plugin(name = "Sentry", type = "Sentry", elementType = "appender")
public class SentryAppender extends AbstractAppender<String> {
    public static final String APPENDER_NAME = "raven";
    private static final String LOG4J_NDC = "Log4J-NDC";
    private final boolean propagateClose;
    private Raven raven;
    private String dsn;
    private String ravenFactory;

    public SentryAppender() {
        this(APPENDER_NAME, PatternLayout.createLayout(null, null, null, null), null, true);
    }


    public SentryAppender(Raven raven) {
        this(raven, false);
    }

    public SentryAppender(Raven raven, boolean propagateClose) {
        this(APPENDER_NAME, PatternLayout.createLayout(null, null, null, null), null, propagateClose);
        this.raven = raven;
    }

    private SentryAppender(String name, Layout<String> layout, Filter filter, boolean propagateClose) {
        super(name, filter, layout, true);
        this.propagateClose = propagateClose;
    }

    /**
     * Create a Sentry Appender.
     *
     * @param name   The name of the Appender.
     * @param dsn    Data Source Name to access the Sentry server.
     * @param layout The layout to use to format the event. If no layout is provided the default PatternLayout
     *               will be used.
     * @param filter The filter, if any, to use.
     * @return The SentryAppender.
     */
    @PluginFactory
    public static SentryAppender createAppender(@PluginAttr("name") final String name,
                                                @PluginAttr("dsn") final String dsn,
                                                @PluginAttr("ravenFactory") final String ravenFactory,
                                                @PluginElement("layout") Layout<String> layout,
                                                @PluginElement("filters") final Filter filter) {

        if (name == null) {
            LOGGER.error("No name provided for FileAppender");
            return null;
        }

        if (layout == null) {
            layout = PatternLayout.createLayout(null, null, null, null);
        }
        SentryAppender sentryAppender = new SentryAppender(name, layout, filter, true);
        sentryAppender.setDsn(dsn);
        sentryAppender.setRavenFactory(ravenFactory);
        return sentryAppender;
    }

    private static Event.Level formatLevel(Level level) {
        switch (level) {
            case FATAL:
                return Event.Level.FATAL;
            case ERROR:
                return Event.Level.ERROR;
            case WARN:
                return Event.Level.WARNING;
            case INFO:
                return Event.Level.INFO;
            case DEBUG:
            case TRACE:
                return Event.Level.DEBUG;
            default:
                return null;
        }
    }

    private static String formatCulprit(StackTraceElement stackTraceElement) {
        return stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName()
                + " at line " + stackTraceElement.getLineNumber();
    }

    @Override
    public void start() {
        if (raven == null) {
            if (dsn == null)
                dsn = Dsn.dsnLookup();

            raven = RavenFactory.ravenInstance(new Dsn(dsn), ravenFactory);
        }
    }

    @Override
    public void append(LogEvent event) {
        Message eventMessage = event.getMessage();
        EventBuilder eventBuilder = new EventBuilder()
                .setTimestamp(new Date(event.getMillis()))
                .setMessage(eventMessage.getFormattedMessage())
                .setLogger(event.getLoggerName())
                .setLevel(formatLevel(event.getLevel()));

        if (event.getThrown() != null) {
            Throwable throwable = event.getThrown();
            eventBuilder.addSentryInterface(new ExceptionInterface(throwable));
        } else if (event.getSource() != null) {
            // When it's a message try to rely on the position of the log (the same message can be logged from
            // different places, or a same place can log a message in different ways).
            String source = formatCulprit(event.getSource());
            eventBuilder.generateChecksum(source);
        }

        if (event.getSource() != null) {
            eventBuilder.setCulprit(event.getSource());
        } else {
            eventBuilder.setCulprit(event.getLoggerName());
        }

        if (!eventMessage.getFormattedMessage().equals(eventMessage.getFormat())) {
            eventBuilder.addSentryInterface(new MessageInterface(eventMessage.getFormat(),
                    formatMessageParameters(eventMessage.getParameters())));
        }

        if (event.getContextStack() != null) {
            eventBuilder.addExtra(LOG4J_NDC, event.getContextStack().asList());
        }

        if (event.getContextMap() != null) {
            for (Map.Entry<String, String> mdcEntry : event.getContextMap().entrySet()) {
                eventBuilder.addExtra(mdcEntry.getKey(), mdcEntry.getValue());
            }
        }

        raven.runBuilderHelpers(eventBuilder);

        raven.sendEvent(eventBuilder.build());
    }

    private List<String> formatMessageParameters(Object[] parameters) {
        List<String> stringParameters = new ArrayList<String>(parameters.length);
        for (Object parameter : parameters)
            stringParameters.add(parameter.toString());
        return stringParameters;
    }

    public void setDsn(String dsn) {
        this.dsn = dsn;
    }

    public void setRavenFactory(String ravenFactory) {
        this.ravenFactory = ravenFactory;
    }

    @Override
    public void stop() {
        super.stop();

        try {
            if (propagateClose)
                raven.getConnection().close();
        } catch (IOException e) {
            error("An exception occurred while closing the raven connection", e);
        }
    }
}
