package com.getsentry.raven.android;

import android.util.Log;

class RavenUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = RavenUncaughtExceptionHandler.class.getName();

    private com.getsentry.raven.Raven raven;
    private Thread.UncaughtExceptionHandler defaultExceptionHandler;

    public RavenUncaughtExceptionHandler(com.getsentry.raven.Raven raven, Thread.UncaughtExceptionHandler defaultExceptionHandler) {
        this.raven = raven;
        this.defaultExceptionHandler = defaultExceptionHandler;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable thrown) {
        Log.d(TAG, "uncaught exception received");

        try {
            raven.sendException(thrown);
        } catch (Exception e) {
            Log.e(TAG, "error sending excepting to Sentry", e);
        }

        // call the original handler
        defaultExceptionHandler.uncaughtException(thread, thrown);
    }

}
