package com.getsentry.raven.android;

import android.util.Log;

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
        Log.d(TAG, "uncaught exception received");

        try {
            com.getsentry.raven.Raven.capture(thrown);
        } catch (Exception e) {
            Log.e(TAG, "error sending excepting to Sentry", e);
        }

        // call the original handler
        defaultExceptionHandler.uncaughtException(thread, thrown);
    }

}
