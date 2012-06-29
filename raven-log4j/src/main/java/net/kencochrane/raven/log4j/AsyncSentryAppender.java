package net.kencochrane.raven.log4j;

import org.apache.log4j.AsyncAppender;

/**
 * TODO Check if this works
 */
public class AsyncSentryAppender extends AsyncAppender {

    private String sentryDsn;
    private SentryAppender appender;

    public String getSentryDsn() {
        return sentryDsn;
    }

    public void setSentryDsn(String sentryDsn) {
        synchronized (this) {
            this.sentryDsn = sentryDsn;
            if (appender != null) {
                removeAppender(appender);
            }
            SentryAppender appender = new SentryAppender();
            appender.setSentryDsn(sentryDsn);
            appender.setErrorHandler(this.getErrorHandler());
            appender.setLayout(this.getLayout());
            appender.setName(this.getName());
            appender.setThreshold(this.getThreshold());
            this.appender = appender;
            addAppender(appender);
        }
    }

}
