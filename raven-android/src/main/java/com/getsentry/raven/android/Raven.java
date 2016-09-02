package com.getsentry.raven.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;
import com.getsentry.raven.dsn.Dsn;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;

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
     * Option for maximum number of events to cache offline when network is down.
     */
    public static final String EVENTCACHE_SIZE_OPTION = "raven.eventcache.size";
    /**
     * Default number of events to cache offline when network is down.
     */
    public static final int EVENTCACHE_SIZE_DEFAULT = 50;

    /**
     * Android application context.
     */
    private static volatile Context context;
    private static volatile com.getsentry.raven.Raven raven;
    private static volatile EventCache eventCache;

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
        if (raven != null) {
            throw new IllegalStateException("Attempted to initialize Raven multiple times.");
        }

        context = ctx.getApplicationContext();

        if (!Util.checkPermission(context, Manifest.permission.INTERNET)) {
            Log.e(TAG, Manifest.permission.INTERNET + " is required to connect to the Sentry server,"
                + " please add it to your AndroidManifest.xml");
        }

        Log.d(TAG, "Raven init with ctx='" + ctx.toString() + "' and dsn='" + dsn + "'");

        int eventCacheSize = EVENTCACHE_SIZE_DEFAULT;
        if (dsn.getOptions().containsKey(EVENTCACHE_SIZE_OPTION)) {
            eventCacheSize = Integer.parseInt(dsn.getOptions().get(EVENTCACHE_SIZE_OPTION));
        }

        eventCache = new EventCache(context, eventCacheSize);
        raven = new RavenFactory(context, eventCache).createRavenInstance(dsn);

        setupUncaughtExceptionHandler();

        if (Util.shouldAttemptToSend(context)) {
            eventCache.flushEvents();
        }
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

    /**
     * Send an Event using the statically stored Raven instance.
     *
     * @param event Event to send to the Sentry server
     */
    public static void capture(Event event) {
        raven.sendEvent(event);
    }

    /**
     * Sends an exception (or throwable) to the Sentry server using the statically stored Raven instance.
     * <p>
     * The exception will be logged at the {@link Event.Level#ERROR} level.
     *
     * @param throwable exception to send to Sentry.
     */
    public static void capture(Throwable throwable) {
        raven.sendException(throwable);
    }

    /**
     * Sends a message to the Sentry server using the statically stored Raven instance.
     * <p>
     * The message will be logged at the {@link Event.Level#INFO} level.
     *
     * @param message message to send to Sentry.
     */
    public static void capture(String message) {
        raven.sendMessage(message);
    }

    /**
     * Builds and sends an {@link Event} to the Sentry server using the statically stored Raven instance.
     *
     * @param eventBuilder {@link EventBuilder} to send to Sentry.
     */
    public static void capture(EventBuilder eventBuilder) {
        raven.sendEvent(eventBuilder);
    }

}
