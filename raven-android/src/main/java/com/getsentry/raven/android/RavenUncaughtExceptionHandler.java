package com.getsentry.raven.android;

import android.util.Log;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.event.interfaces.ExceptionInterface;

/**
 * Sends any uncaught exception to Sentry, then passes the exception on to the pre-existing
 * uncaught exception handler.
 */
class RavenUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    /**
     * Logger tag.
     */
    private static final String TAG = RavenUncaughtExceptionHandler.class.getName();

    /**
     * Reference to the pre-existing uncaught exception handler.
     */
    private Thread.UncaughtExceptionHandler defaultExceptionHandler;

    /**
     * Construct the {@link RavenUncaughtExceptionHandler}, storing the pre-existing uncaught exception
     * handler.
     *
     * @param defaultExceptionHandler pre-existing uncaught exception handler
     */
    RavenUncaughtExceptionHandler(Thread.UncaughtExceptionHandler defaultExceptionHandler) {
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
            com.getsentry.raven.Raven.capture(eventBuilder);
        } catch (Exception e) {
            Log.e(TAG, "Error sending excepting to Sentry.", e);
        }

        if (defaultExceptionHandler != null) {
            // call the original handler
            defaultExceptionHandler.uncaughtException(thread, thrown);
        }
    }

}
