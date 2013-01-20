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
    private String proxy;
    private int queue_size;
    private boolean blocking;
    private boolean naiveSsl;

    public SentryAppender()
    {
        queue_size = 1000;
        blocking = false;
    }

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

    public int getQueue_size() {
        return queue_size;
    }

    public void setQueue_size(int queue_size) {
        this.queue_size = queue_size;
    }

    public boolean getBlocking() {
        return blocking;
    }

    public void setBlocking(boolean blocking) {
        this.blocking = blocking;
    }

    public boolean isNaiveSsl() {
        return naiveSsl;
    }

    public void setNaiveSsl(boolean naiveSsl) {
        this.naiveSsl = naiveSsl;
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

        synchronized (this)
        {
            if(!SentryQueue.getInstance().isSetup())
            {
                SentryQueue.getInstance().setup(sentryDSN, getProxy(), queue_size, blocking, naiveSsl);
            }
        }

        SentryQueue.getInstance().addEvent(loggingEvent);
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
