package net.kencochrane.raven.log4j2;

import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
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

import java.io.IOException;
import java.util.Date;
import java.util.Map;

@Plugin(name = "Sentry", type = "Sentry", elementType = "appender")
public class SentryAppender extends AbstractAppender<String> {
    public static final String APPENDER_NAME = "raven";
    private static final String LOG4J_NDC = "Log4J-NDC";
    private final boolean propagateClose;
    private Raven raven;
    private String dsn;

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
        if (raven == null)
            raven = (dsn != null) ? new Raven(dsn) : new Raven();
    }

    @Override
    public void append(LogEvent event) {
        EventBuilder eventBuilder = new EventBuilder()
                .setTimestamp(new Date(event.getMillis()))
                .setMessage(event.getMessage().getFormattedMessage())
                .setLogger(event.getLoggerName())
                .setLevel(formatLevel(event.getLevel()))
                .setCulprit(formatCulprit(event.getSource()));

        if (event.getThrown() != null) {
            Throwable throwable = event.getThrown();
            eventBuilder.addSentryInterface(new ExceptionInterface(throwable));
            eventBuilder.setCulprit(throwable);
        } else {
            // When it's a message try to rely on the position of the log (the same message can be logged from
            // different places, or a same place can log a message in different ways.
            eventBuilder.generateChecksum(formatCulprit(event.getSource()));
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

    public void setDsn(String dsn) {
        this.dsn = dsn;
    }

    @Override
    public void stop() {
        super.stop();

        try {
            if (propagateClose)
                raven.getConnection().close();
        } catch (IOException e) {
            //TODO: What to do with that exception?
            e.printStackTrace();
        }
    }
}
