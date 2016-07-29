package com.getsentry.raven.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;
import com.getsentry.raven.DefaultRavenFactory;
import com.getsentry.raven.dsn.Dsn;

public class Raven {

    public static final String TAG = Raven.class.getName();

    private static volatile Context context;

    public static void init(Context context) {
        init(context, null);
    }

    public static void init(Context context, String dsnStr) {
        context = context.getApplicationContext();

        if (!checkPermission(context, Manifest.permission.INTERNET)) {
            Log.e(TAG, Manifest.permission.INTERNET + " is required to connect to the Sentry server,"
                + " please add it to your AndroidManifest.xml");
        }

        if (TextUtils.isEmpty(dsnStr)) {
            // attempt to get DSN from AndroidManifest
            ApplicationInfo appInfo = null;
            try {
                PackageManager packageManager = context.getPackageManager();
                appInfo = packageManager.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
                dsnStr = appInfo.metaData.getString("com.getsentry.raven.android.DSN");
            } catch (PackageManager.NameNotFoundException e) {
                // skip
            }
        }

        if (TextUtils.isEmpty(dsnStr)) {
            throw new NullPointerException("Raven DSN is not set, you must provide it via constructor or AndroidManifest.");
        }

        Log.d(TAG, "raven init with context='" + context.toString() + "' and dsn='" + dsnStr + "'");

        Dsn dsn = new Dsn(dsnStr);
        if ("false".equalsIgnoreCase(dsn.getOptions().get(DefaultRavenFactory.ASYNC_OPTION))) {
            throw new IllegalArgumentException("Raven Android cannot use synchronous connections, remove '"
                + DefaultRavenFactory.ASYNC_OPTION + "=false' from your DSN.");
        }

        // actual instance is stored statically on Raven
        RavenFactory.ravenInstance(dsn);
        setupUncaughtExceptionHandler();
        // TODO: sendAllCachedCapturedEvents();
    }

    private static boolean checkPermission(Context context, String permission) {
        int res = context.checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    private static boolean isConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private static boolean shouldAttemptToSend() {
        if (!checkPermission(context, android.Manifest.permission.ACCESS_NETWORK_STATE)) {
            // we can't check whether the connection is up, so the
            // best we can do is try
            return true;
        }

        return isConnected(context);
    }

    private static void setupUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler currentHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (currentHandler != null) {
            Log.d(TAG, "default UncaughtExceptionHandler class='" + currentHandler.getClass().getName() + "'");
        }

        // don't double register
        if (!(currentHandler instanceof RavenUncaughtExceptionHandler)) {
            // register as default exception handler
            Thread.setDefaultUncaughtExceptionHandler(
                new RavenUncaughtExceptionHandler(currentHandler));
        }
    }

}
