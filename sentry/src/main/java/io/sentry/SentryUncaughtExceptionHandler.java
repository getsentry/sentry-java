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
     * Whether or not this instance is enabled. If it has been wrapped by another
     * handler (and is therefore not the {@link Thread#getDefaultUncaughtExceptionHandler()}),
     * this boolean is the only way we can disable it.
     */
    private volatile Boolean enabled = true;

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
        if (enabled) {
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
        }

        // taken from ThreadGroup#uncaughtException
        if (defaultExceptionHandler != null) {
            // call the original handler
            defaultExceptionHandler.uncaughtException(thread, thrown);
        } else if (!(thrown instanceof ThreadDeath)) {
            // CHECKSTYLE.OFF: RegexpSinglelineJava
            System.err.print("Exception in thread \"" + thread.getName() + "\" ");
            thrown.printStackTrace(System.err);
            // CHECKSTYLE.ON: RegexpSinglelineJava
        }
    }

    /**
     * Configures an uncaught exception handler which sends events to
     * Sentry, then calls the preexisting uncaught exception handler.
     *
     * @return {@link SentryUncaughtExceptionHandler} that was setup.
     */
    public static SentryUncaughtExceptionHandler setup() {
        logger.debug("Configuring uncaught exception handler.");

        Thread.UncaughtExceptionHandler currentHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (currentHandler != null) {
            logger.debug("default UncaughtExceptionHandler class='" + currentHandler.getClass().getName() + "'");
        }

        SentryUncaughtExceptionHandler handler = new SentryUncaughtExceptionHandler(currentHandler);
        Thread.setDefaultUncaughtExceptionHandler(handler);
        return handler;
    }

    /**
     * Disable this instance and attempt to remove it as the default {@link Thread.UncaughtExceptionHandler}.
     */
    public void disable() {
        enabled = false;

        // It's possible that another uncaught exception handler was installed 'over' us.
        // Whether or not it wrapped us, we have no control over what other classes do, and
        // so we can only remove ourselves if we are still the ('top most') default handler.
        // The 'enabled' boolean exists to ensure we no longer handle uncaught exceptions
        // even in the scenario where we are wrapped.
        Thread.UncaughtExceptionHandler currentHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (currentHandler == this) {
            Thread.setDefaultUncaughtExceptionHandler(defaultExceptionHandler);
        }
    }

    public Boolean isEnabled() {
        return enabled;
    }

}
