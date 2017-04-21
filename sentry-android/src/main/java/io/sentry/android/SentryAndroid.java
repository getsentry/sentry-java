package io.sentry.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;
import io.sentry.DefaultSentryClientFactory;
import io.sentry.SentryClient;
import io.sentry.SentryClientFactory;
import io.sentry.dsn.Dsn;

/**
 * Android specific class to interface with Sentry. Supplements the default Java classes
 * with Android specific state and features.
 */
public final class SentryAndroid {

    /**
     * Logger tag.
     */
    public static final String TAG = SentryAndroid.class.getName();

    /**
     * Hide constructor.
     */
    private SentryAndroid() {

    }

    /**
     * Initialize Sentry using a DSN set in the AndroidManifest.
     *
     * @param ctx Android application ctx
     * @return SentryClient
     */
    public static SentryClient init(Context ctx) {
        return init(ctx, getDefaultSentryClientFactory(ctx));
    }

    /**
     * Initialize Sentry using a DSN set in the AndroidManifest.
     *
     * @param ctx Android application ctx
     * @param sentryClientFactory the SentryClientFactory to be used to generate the {@link SentryClient} instance
     * @return SentryClient
     */
    public static SentryClient init(Context ctx, AndroidSentryClientFactory sentryClientFactory) {
        ctx = ctx.getApplicationContext();
        String dsn = "";

        // attempt to get DSN from AndroidManifest
        ApplicationInfo appInfo = null;
        try {
            PackageManager packageManager = ctx.getPackageManager();
            appInfo = packageManager.getApplicationInfo(ctx.getPackageName(), PackageManager.GET_META_DATA);
            dsn = appInfo.metaData.getString("io.sentry.android.DSN");
        } catch (PackageManager.NameNotFoundException e) {
            // skip
        }

        if (TextUtils.isEmpty(dsn)) {
            throw new NullPointerException("Sentry DSN is not set, you must provide it via"
                + "the constructor or AndroidManifest.");
        }

        return init(ctx, dsn, sentryClientFactory);
    }

    /**
     * Initialize Sentry using a string DSN.
     *
     * @param ctx Android application ctx
     * @param dsn Sentry DSN string
     * @return SentryClient
     */
    public static SentryClient init(Context ctx, String dsn) {
        return init(ctx, new Dsn(dsn), getDefaultSentryClientFactory(ctx));
    }

    /**
     * Initialize Sentry using a string DSN.
     *
     * @param ctx Android application ctx
     * @param dsn Sentry DSN string
     * @param sentryClientFactory the SentryClientFactory to be used to generate the SentryClient
     * @return SentryClient
     */
    public static SentryClient init(Context ctx, String dsn, AndroidSentryClientFactory sentryClientFactory) {
        return init(ctx, new Dsn(dsn), sentryClientFactory);
    }

    /**
     * Initialize Sentry using a DSN object.
     *
     * @param ctx Android application ctx
     * @param dsn Sentry DSN object
     * @return SentryClient
     */
    public static SentryClient init(Context ctx, Dsn dsn) {
        return init(ctx, dsn, getDefaultSentryClientFactory(ctx));
    }

    /**
     * Initialize Sentry using a DSN object. This is the 'main' initializer that other methods
     * eventually call.
     *
     * @param ctx Android application ctx
     * @param dsn Sentry DSN object
     * @param sentryClientFactory the SentryClientFactory to be used to generate the {@link SentryClient} instance
     * @return SentryClient
     */
    public static SentryClient init(Context ctx, Dsn dsn, AndroidSentryClientFactory sentryClientFactory) {
        // Ensure we have the application context
        Context context = ctx.getApplicationContext();

        if (!Util.checkPermission(context, Manifest.permission.INTERNET)) {
            Log.e(TAG, Manifest.permission.INTERNET + " is required to connect to the Sentry server,"
                + " please add it to your AndroidManifest.xml");
        }

        Log.d(TAG, "Sentry init with ctx='" + ctx.toString() + "' and dsn='" + dsn + "'");

        String protocol = dsn.getProtocol();
        if (!(protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only 'http' or 'https' connections are supported in"
                + " Sentry Android, but received: " + protocol);
        }

        if ("false".equalsIgnoreCase(dsn.getOptions().get(DefaultSentryClientFactory.ASYNC_OPTION))) {
            throw new IllegalArgumentException("Sentry Android cannot use synchronous connections, remove '"
                + DefaultSentryClientFactory.ASYNC_OPTION + "=false' from your DSN.");
        }

        SentryClientFactory.registerFactory(sentryClientFactory);

        // SentryClient will store the instance statically on the Sentry utility class.
        SentryClient sentryClient = SentryClientFactory.sentryClient(dsn);
        setupUncaughtExceptionHandler();
        return sentryClient;
    }

    private static AndroidSentryClientFactory getDefaultSentryClientFactory(Context ctx) {
        return new AndroidSentryClientFactory(ctx);
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
        if (!(currentHandler instanceof SentryUncaughtExceptionHandler)) {
            // register as default exception handler
            Thread.setDefaultUncaughtExceptionHandler(
                new SentryUncaughtExceptionHandler(currentHandler));
        }
    }

}
