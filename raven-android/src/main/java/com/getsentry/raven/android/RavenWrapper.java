package com.getsentry.raven.android;

import android.content.Context;
import android.util.Log;

import com.getsentry.raven.Raven;

/**
 * Wraps a AndroidRaven instance so we can keep some useful Android state alongside it.
 */
public class RavenWrapper {

    private final Raven raven;
    private final Context context;

    public RavenWrapper(Raven raven, Context context) {
        this.raven = raven;
        this.context = context;
    }

    public void setupUncaughtExceptionHandler() {

        Thread.UncaughtExceptionHandler currentHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (currentHandler != null) {
            Log.d(AndroidRaven.TAG, "default UncaughtExceptionHandler class='" + currentHandler.getClass().getName() + "'");
        }

        // don't double register
        if (!(currentHandler instanceof RavenUncaughtExceptionHandler)) {
            // register as default exception handler
            Thread.setDefaultUncaughtExceptionHandler(
                    new RavenUncaughtExceptionHandler(raven, currentHandler));
        }
    }

    public Raven getRaven() {
        return raven;
    }

    public Context getContext() {
        return context;
    }

}
