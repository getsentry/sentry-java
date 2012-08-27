package net.kencochrane.raven.log4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.kencochrane.raven.Client;
import net.kencochrane.raven.SentryDsn;
import net.kencochrane.raven.spi.JSONProcessor;
import net.kencochrane.raven.spi.RavenMDC;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

/**
 * Log4J appender that will send messages to Sentry.
 */
public class SentryAppender extends AppenderSkeleton {

    private Log4jMDC mdc;
    protected String sentryDsn;
    protected Client client;
    private String jsonProcessors;

    public SentryAppender() {
        Utils.initMDC();
        mdc = (Log4jMDC)RavenMDC.getInstance();
    }

    public String getSentryDsn() {
        return sentryDsn;
    }

    public void setSentryDsn(String sentryDsn) {
        synchronized (this) {
            this.sentryDsn = sentryDsn;
            if (client != null) {
                client.stop();
                client = null;
            }
        }
    }

    /**
     * Set a comma-separated list of fully qualified class names of
     * JSONProcessors to be used.
     *
     * @param jsonProcessors a comma-separated list of fully qualified class
     * 		names of JSONProcessors
     */
    public void setJsonProcessors(String jsonProcessors) {
        this.jsonProcessors = jsonProcessors;
    }

    @Override
    public void close() {
        if (client != null) {
            client.stop();
        }
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }

    @Override
    protected void append(LoggingEvent event) {
        mdc.setThreadLoggingEvent(event);
        try {
            Client client = fetchClient();
            // get timestamp and timestamp in correct string format.
            long timestamp = event.getTimeStamp();

            // get the log and info about the log.
            String message = event.getRenderedMessage();
            String logger = event.getLogger().getName();
            int level = (event.getLevel().toInt() / 1000);  //Need to divide by 1000 to keep consistent with sentry
            String culprit = event.getLoggerName();

            // is it an exception?
            ThrowableInformation info = event.getThrowableInformation();

            // send the message to the sentry server
            if (info == null) {
                client.captureMessage(message, timestamp, logger, level, culprit);
            } else {
                client.captureException(message, timestamp, logger, level, culprit, info.getThrowable());
            }
        } finally {
            mdc.removeThreadLoggingEvent();
        }
    }

    /**
     * Use this to refer to the client instead of using {@link #client} directly.
     * <p>
     * This makes sure a client is available.
     * </p>
     *
     * @return the client
     */
    protected synchronized Client fetchClient() {
        if (client == null) {
            if (sentryDsn == null) {
                client = new Client();
            } else {
                client = new Client(SentryDsn.build(sentryDsn));
            }
            client.setJSONProcessors(loadJSONProcessors());
        }
        return client;
    }

    private List<JSONProcessor> loadJSONProcessors() {
        if (jsonProcessors == null) {
            return Collections.emptyList();
        }
        try {
            List<JSONProcessor> processors = new ArrayList<JSONProcessor>();
            String[] clazzes = jsonProcessors.split(",\\s*");
            for (String clazz : clazzes) {
                JSONProcessor processor = (JSONProcessor)Class.forName(clazz).newInstance();
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

}
