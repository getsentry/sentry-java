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

        SentryQueue.getInstance().addEvent(loggingEvent, sentryDSN, getProxy());
    }

    public void close()
    {
        SentryQueue.getInstance().shutdown();
    }

    public boolean requiresLayout()
    {
        return false;
    }
}
