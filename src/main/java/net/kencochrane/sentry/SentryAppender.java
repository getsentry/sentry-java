package net.kencochrane.sentry;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

/**
 * User: ken cochrane
 * Date: 2/6/12
 * Time: 10:54 AM
 */
public class SentryAppender extends AppenderSkeleton {

    private String sentry_dsn;
    private String proxy;

    public String getSentry_dsn() {
        return sentry_dsn;
    }

    public void setSentry_dsn(String sentry_dsn) {
        this.sentry_dsn = sentry_dsn;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    /**
    * Look for the ENV variable first, and if it isn't there, then look in the log4j properties
    *
    */
    private String findSentryDSN(){
        String sentryDSN = System.getenv("SENTRY_DSN");
        if (sentryDSN == null || sentryDSN.length() == 0) {
            sentryDSN = getSentry_dsn();
            if (sentryDSN == null) {
                throw new RuntimeException("ERROR: You do not have a Sentry DSN configured! make sure you add sentry_dsn to your log4j properties, or have set SENTRY_DSN as an envirornment variable.");
            }
        }
        return sentryDSN;
    }

    @Override
    protected void append(LoggingEvent loggingEvent) {

        //find the sentry DSN.
        String sentryDSN = findSentryDSN();

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
                RavenClient client = new RavenClient(sentryDSN, getProxy());

                // is it an exception?
                ThrowableInformation throwableInformation = loggingEvent.getThrowableInformation();
                
                // send the message to the sentry server
                if (throwableInformation == null){
                    client.captureMessage(logMessage, timestamp, loggingClass, logLevel, culprit);
                }else{
                    client.captureException(logMessage, timestamp, loggingClass, logLevel, culprit, throwableInformation.getThrowable());
                }

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
