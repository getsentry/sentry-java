package com.getsentry.raven.android;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class Raven {

    public static final String TAG = Raven.class.getName();

    private static volatile Context context;
    private static volatile com.getsentry.raven.Raven raven;

    public static void init(Context context, String dsn) {
        Log.d(TAG, "raven init with context='" + context.toString() + "' and dsn='" + dsn + "'");

        // we always want the app context
        context = context.getApplicationContext();
        raven = RavenFactory.ravenInstance(dsn);

        setupUncaughtExceptionHandler();
        // TODO: sendAllCachedCapturedEvents();
    }

    // TODO: check before send
    // String permission = android.Manifest.permission.ACCESS_NETWORK_STATE;
    // String permission = android.Manifest.permission.INTERNET;
    private static boolean checkPermission(Context context, String permission) {
        int res = context.checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    // TODO: check before send
    private static boolean isConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private static void setupUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler currentHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (currentHandler != null) {
            Log.d(Raven.TAG, "default UncaughtExceptionHandler class='" + currentHandler.getClass().getName() + "'");
        }

        // don't double register
        if (!(currentHandler instanceof RavenUncaughtExceptionHandler)) {
            // register as default exception handler
            Thread.setDefaultUncaughtExceptionHandler(
                new RavenUncaughtExceptionHandler(raven, currentHandler));
        }
    }

}
