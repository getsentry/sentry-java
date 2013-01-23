package net.kencochrane.raven.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import net.kencochrane.raven.Client;
import net.kencochrane.raven.SentryDsn;
import net.kencochrane.raven.spi.JSONProcessor;
import net.kencochrane.raven.spi.RavenMDC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Logback appender that will send messages to Sentry.
 */
public class SentryAppender extends AppenderBase<ILoggingEvent> {

    private boolean async;
    private LogbackMDC mdc;
    protected String sentryDsn;
    protected Client client;
    protected boolean messageCompressionEnabled = true;
    private List<JSONProcessor> jsonProcessors = Collections.emptyList();

    public SentryAppender() {
        initMDC();
        mdc = (LogbackMDC) RavenMDC.getInstance();
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public String getSentryDsn() {
        return sentryDsn;
    }

    public void setSentryDsn(String sentryDsn) {
        this.sentryDsn = sentryDsn;
    }

    public boolean isMessageCompressionEnabled() {
        return messageCompressionEnabled;
    }

    public void setMessageCompressionEnabled(boolean messageCompressionEnabled) {
        this.messageCompressionEnabled = messageCompressionEnabled;
    }

    /**
     * Set a comma-separated list of fully qualified class names of
     * JSONProcessors to be used.
     *
     * @param setting a comma-separated list of fully qualified class names of
     *                JSONProcessors
     */
    public void setJsonProcessors(String setting) {
        this.jsonProcessors = loadJSONProcessors(setting);
    }

    /**
     * Notify processors that a message has been logged. Note that this method
     * is intended to be run on the same thread that creates the message.
     */
    public void notifyProcessorsBeforeAppending() {
        for (JSONProcessor processor : jsonProcessors) {
            processor.prepareDiagnosticContext();
        }
    }

    /**
     * Notify processors after a message has been logged. Note that this method
     * is intended to be run on the same thread that creates the message.
     */
    public void notifyProcessorsAfterAppending() {
        for (JSONProcessor processor : jsonProcessors) {
            processor.clearDiagnosticContext();
        }
    }

    @Override
    public void start() {
        activateOptions();
        super.start();
    }

    @Override
    public void stop() {
        if (client != null) {
            client.stop();
        }
        super.stop();
    }

    public void activateOptions() {
        client = sentryDsn == null ? new Client() : new Client(SentryDsn.buildOptional(sentryDsn));
        client.setJSONProcessors(jsonProcessors);
        client.setMessageCompressionEnabled(messageCompressionEnabled);
    }

    @Override
    protected void append(ILoggingEvent event) {
        mdc.setThreadLoggingEvent(event);
        try {
            // get timestamp and timestamp in correct string format.
            long timestamp = event.getTimeStamp();

            // get the log and info about the log.
            String message = event.getMessage();
            String logger = event.getLoggerName();
            int level = event.getLevel().toInt() / 1000; // Need to divide by
            // 1000 to keep
            // consistent with
            // sentry
            String culprit = event.getLoggerName();

            IThrowableProxy throwable = event.getThrowableProxy();

            // notify processors about the message
            // (in async mode this is done by AsyncSentryAppender)
            if (!async) {
                notifyProcessorsBeforeAppending();
            }

            // send the message to the sentry server
            if (event.getThrowableProxy() == null) {
                client.captureMessage(message, timestamp, logger, level, culprit);
            } else {
                client.captureException(message, timestamp, logger, level, culprit, ((ThrowableProxy) throwable).getThrowable());
            }

            if (!async) {
                notifyProcessorsAfterAppending();
            }
        } finally {
            mdc.removeThreadLoggingEvent();
        }
    }

    private static List<JSONProcessor> loadJSONProcessors(String setting) {
        if (setting == null) {
            return Collections.emptyList();
        }
        try {
            List<JSONProcessor> processors = new ArrayList<JSONProcessor>();
            String[] clazzes = setting.split(",\\s*");
            for (String clazz : clazzes) {
                JSONProcessor processor = (JSONProcessor) Class.forName(clazz).newInstance();
                processors.add(processor);
            }
            return processors;
        } catch (ClassNotFoundException exception) {
            throw new RuntimeException("Processor could not be found.", exception);
        } catch (InstantiationException exception) {
            throw new RuntimeException("Processor could not be instantiated.", exception);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException("Processor could not be instantiated.", exception);
        }
    }

    public static void initMDC() {
        if (RavenMDC.getInstance() != null) {
            if (!(RavenMDC.getInstance() instanceof LogbackMDC)) {
                throw new IllegalStateException("An incompatible RavenMDC instance has been set. Please check your Raven configuration.");
            }
            return;
        }
        RavenMDC.setInstance(new LogbackMDC());
    }

}
