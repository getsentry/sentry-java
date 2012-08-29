package net.kencochrane.raven.log4j;

import net.kencochrane.raven.SentryDsn;
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.spi.LoggingEvent;

/**
 * A Log4J appender that sends events asynchronously to Sentry.
 * <p>
 * This appender extends Log4J's {@link AsyncAppender}. If you use a log4j.xml file to configure Log4J, you can use that
 * class directly to wrap a {@link SentryAppender}. If you use a log4j.properties file -- which doesn't provide a way to
 * configure the {@link AsyncAppender} -- to configure Log4J and you need to send events asynchronously, you can use
 * this class instead.
 * </p>
 */
public class AsyncSentryAppender extends AsyncAppender {

    private String sentryDsn;
    private String jsonProcessors;
    private SentryAppender appender;

    public AsyncSentryAppender() {
        SentryAppender.initMDC();
    }

    public String getSentryDsn() {
        return sentryDsn;
    }

    public void setSentryDsn(String sentryDsn) {
        this.sentryDsn = sentryDsn;
        if (appender != null) {
            removeAppender(appender);
            appender = null;
        }
    }

    /**
     * See {@link SentryAppender#setJsonProcessors}.
     *
     * @param jsonProcessors a comma-separated list of fully qualified class
     *                       names of JSONProcessors
     */
    public void setJsonProcessors(String jsonProcessors) {
        this.jsonProcessors = jsonProcessors;
    }

    @Override
    public void append(LoggingEvent event) {
        appender.notifyProcessors();
        super.append(event);
    }

    @Override
    public void activateOptions() {
        if (sentryDsn == null) {
            setSentryDsn(SentryDsn.build().toString());
        }
        SentryAppender appender = new SentryAppender();
        appender.setAsync(true);
        appender.setSentryDsn(sentryDsn);
        appender.setJsonProcessors(jsonProcessors);
        appender.setErrorHandler(this.getErrorHandler());
        appender.setLayout(this.getLayout());
        appender.setName(this.getName());
        appender.setThreshold(this.getThreshold());
        appender.activateOptions();
        this.appender = appender;
        addAppender(appender);
        super.activateOptions();
    }

}
