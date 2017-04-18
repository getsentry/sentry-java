package io.sentry.android;

import android.util.Log;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;

/**
 * Sends any uncaught exception to Sentry, then passes the exception on to the pre-existing
 * uncaught exception handler.
 */
class SentryUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    /**
     * Logger tag.
     */
    private static final String TAG = SentryUncaughtExceptionHandler.class.getName();

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
    SentryUncaughtExceptionHandler(Thread.UncaughtExceptionHandler defaultExceptionHandler) {
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
        Log.d(TAG, "Uncaught exception received.");

        EventBuilder eventBuilder = new EventBuilder()
            .withMessage(thrown.getMessage())
            .withLevel(Event.Level.FATAL)
            .withSentryInterface(new ExceptionInterface(thrown));

        try {
            io.sentry.Sentry.capture(eventBuilder);
        } catch (Exception e) {
            Log.e(TAG, "Error sending excepting to Sentry.", e);
        }

        if (defaultExceptionHandler != null) {
            // call the original handler
            defaultExceptionHandler.uncaughtException(thread, thrown);
        }
    }

}
