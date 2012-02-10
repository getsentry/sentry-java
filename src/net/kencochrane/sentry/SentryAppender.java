package net.kencochrane.sentry;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

/**
 * User: ken cochrane
 * Date: 2/6/12
 * Time: 10:54 AM
 */
public class SentryAppender extends AppenderSkeleton {

    private String sentry_dsn;

    public String getSentry_dsn() {
        return sentry_dsn;
    }

    public void setSentry_dsn(String sentry_dsn) {
        this.sentry_dsn = sentry_dsn;
    }

    @Override
    protected void append(LoggingEvent loggingEvent) {

        if (getSentry_dsn() == null) {
            System.err.println("ERROR: You do not have a Sentry DSN configured! make sure you add sentry_dsn to your log4j properties ");
            return;
        }

        synchronized (this) {
            try {
                // get timestamp and timestamp in correct string format.
                long timestamp = loggingEvent.getTimeStamp();

                // get the log and info about the log.
                String logMessage = loggingEvent.getRenderedMessage();
                String loggingClass = loggingEvent.getLogger().getName();
                int logLevel = (loggingEvent.getLevel().toInt() / 1000);  //Need to divide by 1000 to keep consistent with sentry
                String culprit = loggingEvent.getLoggerName();

                // create the client passing in the sentry DSN from the log4j properties file.
                RavenClient client = new RavenClient(getSentry_dsn());

                // send the message to the sentry server
                client.logMessage(logMessage, timestamp, loggingClass, logLevel, culprit);

            } catch (Exception e) {
                System.err.println(e);
            }
        }

    }

    public void close() {
        // clean up normally goes here. but there isn't any
    }

    public boolean requiresLayout() {
        return false;
    }
}
