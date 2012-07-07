package net.kencochrane.raven.log4j;

import net.kencochrane.raven.Client;
import net.kencochrane.raven.SentryDsn;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

/**
 * Log4J appender that will send messages to Sentry.
 */
public class SentryAppender extends AppenderSkeleton {

    protected String sentryDsn;
    protected Client client;

    public String getSentryDsn() {
        return sentryDsn;
    }

    public void setSentryDsn(String sentryDsn) {
        synchronized (this) {
            this.sentryDsn = sentryDsn;
            if (client != null) {
                client.stop();
            }
            // Create a client that start automatically
            client = new Client(SentryDsn.build(sentryDsn));
        }
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
            client = new Client();
        }
        return client;
    }

}
