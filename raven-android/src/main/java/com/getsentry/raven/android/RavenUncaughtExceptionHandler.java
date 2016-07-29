package com.getsentry.raven.android;

import android.util.Log;

class RavenUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = RavenUncaughtExceptionHandler.class.getName();

    private Thread.UncaughtExceptionHandler defaultExceptionHandler;

    public RavenUncaughtExceptionHandler(Thread.UncaughtExceptionHandler defaultExceptionHandler) {
        this.defaultExceptionHandler = defaultExceptionHandler;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable thrown) {
        Log.d(TAG, "uncaught exception received");

        try {
            // TODO: Raven.capture
        } catch (Exception e) {
            Log.e(TAG, "error sending excepting to Sentry", e);
        }

        // call the original handler
        defaultExceptionHandler.uncaughtException(thread, thrown);
    }

}
