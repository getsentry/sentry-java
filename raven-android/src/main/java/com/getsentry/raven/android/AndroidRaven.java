package com.getsentry.raven.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;
import com.getsentry.raven.DefaultRavenFactory;
import com.getsentry.raven.RavenFactory;
import com.getsentry.raven.dsn.Dsn;

/**
 * Android specific class to interface with Raven. Supplements the default Java classes
 * with Android specific state and features.
 */
public final class AndroidRaven {

    /**
     * Logger tag.
     */
    public static final String TAG = AndroidRaven.class.getName();

    /**
     * Hide constructor.
     */
    private AndroidRaven() {

    }

    /**
     * Initialize Raven using a DSN set in the AndroidManifest.
     *
     * @param ctx Android application ctx
     */
    public static void init(Context ctx) {
        init(ctx, getDefaultRavenFactory(ctx));
    }

    /**
     * Initialize Raven using a DSN set in the AndroidManifest.
     *
     * @param ctx Android application ctx
     * @param ravenFactory the RavenFactory to be used to generate the Raven instance
     */
    public static void init(Context ctx, AndroidRavenFactory ravenFactory) {
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

        init(ctx, dsn, ravenFactory);
    }

    /**
     * Initialize Raven using a string DSN.
     *
     * @param ctx Android application ctx
     * @param dsn Sentry DSN string
     */
    public static void init(Context ctx, String dsn) {
        init(ctx, new Dsn(dsn), getDefaultRavenFactory(ctx));
    }

    /**
     * Initialize Raven using a string DSN.
     *
     * @param ctx Android application ctx
     * @param dsn Sentry DSN string
     * @param ravenFactory the RavenFactory to be used to generate the Raven instance
     */
    public static void init(Context ctx, String dsn, AndroidRavenFactory ravenFactory) {
        init(ctx, new Dsn(dsn), ravenFactory);
    }

    /**
     * Initialize Raven using a DSN object.
     *
     * @param ctx Android application ctx
     * @param dsn Sentry DSN object
     */
    public static void init(Context ctx, Dsn dsn) {
        init(ctx, dsn, getDefaultRavenFactory(ctx));
    }

    /**
     * Initialize Raven using a DSN object. This is the 'main' initializer that other methods
     * eventually call.
     *
     * @param ctx Android application ctx
     * @param dsn Sentry DSN object
     * @param ravenFactory the RavenFactory to be used to generate the Raven instance
     */
    public static void init(Context ctx, Dsn dsn, AndroidRavenFactory ravenFactory) {
        // Ensure we have the application context
        Context context = ctx.getApplicationContext();

        if (!Util.checkPermission(context, Manifest.permission.INTERNET)) {
            Log.e(TAG, Manifest.permission.INTERNET + " is required to connect to the Sentry server,"
                + " please add it to your AndroidManifest.xml");
        }

        Log.d(TAG, "Raven init with ctx='" + ctx.toString() + "' and dsn='" + dsn + "'");

        String protocol = dsn.getProtocol();
        if (!(protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only 'http' or 'https' connections are supported in"
                + " Raven Android, but received: " + protocol);
        }

        if ("false".equalsIgnoreCase(dsn.getOptions().get(DefaultRavenFactory.ASYNC_OPTION))) {
            throw new IllegalArgumentException("Raven Android cannot use synchronous connections, remove '"
                + DefaultRavenFactory.ASYNC_OPTION + "=false' from your DSN.");
        }

        RavenFactory.registerFactory(ravenFactory);
        RavenFactory.ravenInstance(dsn);

        setupUncaughtExceptionHandler();
    }

    private static AndroidRavenFactory getDefaultRavenFactory(Context ctx) {
        return new AndroidRavenFactory(ctx);
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
