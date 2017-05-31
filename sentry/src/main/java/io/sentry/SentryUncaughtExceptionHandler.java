package io.sentry;

import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends any uncaught exception to Sentry, then passes the exception on to the pre-existing
 * uncaught exception handler.
 */
public class SentryUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(SentryClientFactory.class);

    /**
     * Reference to the pre-existing uncaught exception handler.
     */
    private Thread.UncaughtExceptionHandler defaultExceptionHandler;

    /**
     * Construct the {@link SentryUncaughtExceptionHandler}, storing the pre-existing uncaught exception
     * handler.
     *
     * @param defaultExceptionHandler pre-existing uncaught exception handler
     */
    public SentryUncaughtExceptionHandler(Thread.UncaughtExceptionHandler defaultExceptionHandler) {
        this.defaultExceptionHandler = defaultExceptionHandler;
    }

    /**
     * Sends any uncaught exception to Sentry, then passes the exception on to the pre-existing
     * uncaught exception handler.
     *
     * @param thread thread that threw the error
     * @param thrown the uncaught throwable
     */
    @Override
    public void uncaughtException(Thread thread, Throwable thrown) {
        logger.trace("Uncaught exception received.");

        EventBuilder eventBuilder = new EventBuilder()
            .withMessage(thrown.getMessage())
            .withLevel(Event.Level.FATAL)
            .withSentryInterface(new ExceptionInterface(thrown));

        try {
            Sentry.capture(eventBuilder);
        } catch (Exception e) {
            logger.error("Error sending uncaught exception to Sentry.", e);
        }

        if (defaultExceptionHandler != null) {
            // call the original handler
            defaultExceptionHandler.uncaughtException(thread, thrown);
        }
    }

    /**
     * Configures an uncaught exception handler which sends events to
     * Sentry, then calls the preexisting uncaught exception handler.
     */
    public static void setup() {
        Thread.UncaughtExceptionHandler currentHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (currentHandler != null) {
            logger.debug("default UncaughtExceptionHandler class='" + currentHandler.getClass().getName() + "'");
        }

        // don't double register
        if (!(currentHandler instanceof SentryUncaughtExceptionHandler)) {
            // register as default exception handler
            Thread.setDefaultUncaughtExceptionHandler(
                new SentryUncaughtExceptionHandler(currentHandler));
        }
    }

}
