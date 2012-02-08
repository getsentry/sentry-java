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

        RavenConfig config = new RavenConfig(getSentry_dsn());

        synchronized (this) {
            try {
                RavenClient client = new RavenClient();
                String message = loggingEvent.getRenderedMessage();

                long timestamp = loggingEvent.getTimeStamp();
                String timestampDate = client.getTimestampString(timestamp);
                String loggingClass = loggingEvent.getLogger().getName();
                int logLevel = (loggingEvent.getLevel().toInt() / 1000);  //Need to divide by 1000 to keep consistent with sentry
                String culprit = loggingEvent.getLoggerName();

                String jsonMessage = client.buildJSON(message, timestampDate, loggingClass, logLevel, culprit, config.getProjectId());
                String messageBody = client.buildMessageBody(jsonMessage);
                String hmacSignature = client.getSignature(messageBody, timestamp, config.getSecretKey());
                client.sendMessage(config.getSentryURL(), messageBody, hmacSignature, config.getPublicKey(), timestamp);

            } catch (Exception e) {
                System.err.println(e);
            }
        }

    }

    public void close() {
        //TODO: cleannup goes here. Is there any?
    }

    public boolean requiresLayout() {
        return false;
    }
}
