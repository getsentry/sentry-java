package com.getsentry.raven.android;

import android.content.Context;
import android.util.Log;

import com.getsentry.raven.Raven;
import com.getsentry.raven.RavenFactory;

public class AndroidRaven {

    public static final String TAG = AndroidRaven.class.getName();

    private static RavenWrapper ravenWrapper;

    public static void init(Context context, String dsn) {
        Log.d(TAG, "raven init with context='" + context.toString() + "' and dsn='" + dsn + "'");

        Raven raven = RavenFactory.ravenInstance(dsn);
        ravenWrapper = new RavenWrapper(raven, context);
        ravenWrapper.setupUncaughtExceptionHandler();
        // TODO: ravenWrapper.sendAllCachedCapturedEvents();
    }

}
