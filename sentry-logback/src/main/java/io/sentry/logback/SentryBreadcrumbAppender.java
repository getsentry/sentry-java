package io.sentry.logback;

import java.util.Date;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.sentry.Sentry;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Breadcrumb;
import io.sentry.event.BreadcrumbBuilder;

/**
 * A logback appender that turns logging events into Breadcrumbs on the ThreadLocal
 * context.
 */
public class SentryBreadcrumbAppender extends AppenderBase<ILoggingEvent> {

    /**
     * The append method for the SentryBreadcrumbAppender adds breadcrumbs to the context
     * for all logging events that it receives.
     * @param iLoggingEvent the event to transform into a breadcrumb
     */
    @Override protected void append(ILoggingEvent iLoggingEvent) {
        // Do not log the event if the current thread is managed by sentry
        if (SentryEnvironment.isManagingThread()) {
            return;
        }
        SentryEnvironment.startManagingThread();
        try {
            BreadcrumbBuilder breadcrumb = new BreadcrumbBuilder()
                    .setLevel(formatLevel(iLoggingEvent.getLevel()))
                    .setMessage(iLoggingEvent.getFormattedMessage())
                    .setTimestamp(new Date(iLoggingEvent.getTimeStamp()));
            Sentry.getContext().recordBreadcrumb(breadcrumb.build());
        } catch (Exception e) {
            addError("An exception occurred while creating a new breadcrumb in Sentry", e);
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }

    /**
     * Transforms a {@link Level} into an {@link Breadcrumb.Level}.
     *
     * @param level original level as defined in logback.
     * @return log level used within sentry.
     */
    protected static Breadcrumb.Level formatLevel(Level level) {
        if (level.isGreaterOrEqual(Level.ERROR)) {
            return Breadcrumb.Level.ERROR;
        } else if (level.isGreaterOrEqual(Level.WARN)) {
            return Breadcrumb.Level.WARNING;
        } else if (level.isGreaterOrEqual(Level.INFO)) {
            return Breadcrumb.Level.INFO;
        } else if (level.isGreaterOrEqual(Level.ALL)) {
            return Breadcrumb.Level.DEBUG;
        } else {
            return null;
        }
    }
}
