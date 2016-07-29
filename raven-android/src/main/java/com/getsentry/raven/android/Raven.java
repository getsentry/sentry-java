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

/**
 * Android specific class to interface with Raven. Supplements the default Java classes
 * with Android specific state and features.
 */
public final class Raven {

    /**
     * Logger tag.
     */
    public static final String TAG = Raven.class.getName();

    /**
     * Android application context.
     */
    private static volatile Context context;

    /**
     * Hide constructor.
     */
    private Raven() {

    }

    /**
     * Initialize Raven using a DSN set in the AndroidManifest.
     *
     * @param ctx Android application ctx
     */
    public static void init(Context ctx) {
        ctx = ctx.getApplicationContext();
        String dsn = "";

        // attempt to get DSN from AndroidManifest
        ApplicationInfo appInfo = null;
        try {
            PackageManager packageManager = ctx.getPackageManager();
            appInfo = packageManager.getApplicationInfo(ctx.getPackageName(), PackageManager.GET_META_DATA);
            dsn = appInfo.metaData.getString("com.getsentry.raven.android.DSN");
        } catch (PackageManager.NameNotFoundException e) {
            // skip
        }

        if (TextUtils.isEmpty(dsn)) {
            throw new NullPointerException("Raven DSN is not set, you must provide it via"
                + "the constructor or AndroidManifest.");
        }

        init(ctx, new Dsn(dsn));
    }

    /**
     * Initialize Raven using a string DSN.
     *
     * @param ctx Android application ctx
     * @param dsn Sentry DSN string
     */
    public static void init(Context ctx, String dsn) {
        init(ctx, new Dsn(dsn));
    }

    /**
     * Initialize Raven using a DSN object. This is the 'main' initializer that other methods
     * eventually call.
     *
     * @param ctx Android application ctx
     * @param dsn Sentry DSN object
     */
    public static void init(Context ctx, Dsn dsn) {
        context = ctx.getApplicationContext();

        if (!checkPermission(ctx, Manifest.permission.INTERNET)) {
            Log.e(TAG, Manifest.permission.INTERNET + " is required to connect to the Sentry server,"
                + " please add it to your AndroidManifest.xml");
        }

        Log.d(TAG, "raven init with ctx='" + ctx.toString() + "' and dsn='" + dsn + "'");
        if ("false".equalsIgnoreCase(dsn.getOptions().get(DefaultRavenFactory.ASYNC_OPTION))) {
            throw new IllegalArgumentException("Raven Android cannot use synchronous connections, remove '"
                + DefaultRavenFactory.ASYNC_OPTION + "=false' from your DSN.");
        }

        // actual instance is stored statically on Raven
        DefaultRavenFactory.ravenInstance(dsn);
        setupUncaughtExceptionHandler();
    }

    /**
     * Check whether the application has been granted a certain permission.
     *
     * @param ctx Android application ctx
     * @param permission Permission as a string
     * @return true if permissions is granted
     */
    private static boolean checkPermission(Context ctx, String permission) {
        int res = ctx.checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Check whether the application has internet access at a point in time.
     *
     * @param ctx Android appliation ctx
     * @return true if the application has internet access
     */
    private static boolean isConnected(Context ctx) {
        ConnectivityManager connectivityManager =
            (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /**
     * Check whether Raven should attempt to send an event, or just immediately store it.
     *
     * @return true if Raven should attempt to send an event
     */
    private static boolean shouldAttemptToSend() {
        if (!checkPermission(context, android.Manifest.permission.ACCESS_NETWORK_STATE)) {
            // we can't check whether the connection is up, so the
            // best we can do is try
            return true;
        }

        return isConnected(context);
    }

    /**
     * Configures an Android uncaught exception handler which sends events to
     * Sentry, then calls the preexisting uncaught exception handler.
     */
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
