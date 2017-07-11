package io.sentry.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import io.sentry.*;
import io.sentry.android.event.helper.AndroidEventBuilderHelper;
import io.sentry.buffer.Buffer;
import io.sentry.buffer.DiskBuffer;
import io.sentry.config.Lookup;
import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
import io.sentry.dsn.Dsn;

import java.io.File;

/**
 * SentryClientFactory that handles Android-specific construction, like taking advantage
 * of the Android Context instance.
 */
public class AndroidSentryClientFactory extends DefaultSentryClientFactory {

    /**
     * Logger tag.
     */
    public static final String TAG = AndroidSentryClientFactory.class.getName();
    /**
     * Default Buffer directory name.
     */
    private static final String DEFAULT_BUFFER_DIR = "sentry-buffered-events";

    private Context ctx;

    /**
     * Construct an AndroidSentryClientFactory using the specified Android Context.
     *
     * @param ctx Android Context.
     */
    public AndroidSentryClientFactory(Context ctx) {
        Log.d(TAG, "Construction of Android Sentry.");

        this.ctx = ctx.getApplicationContext();
    }

    @Override
    public SentryClient createSentryClient(Dsn dsn) {
        if (!checkPermission(Manifest.permission.INTERNET)) {
            Log.e(TAG, Manifest.permission.INTERNET + " is required to connect to the Sentry server,"
                + " please add it to your AndroidManifest.xml");
        }

        Log.d(TAG, "Sentry init with ctx='" + ctx.toString() + "' and dsn='" + dsn + "'");

        String protocol = dsn.getProtocol();
        if (protocol.equalsIgnoreCase("noop")) {
            Log.w(TAG, "*** Couldn't find a suitable DSN, Sentry operations will do nothing!"
                + " See documentation: https://docs.sentry.io/clients/java/modules/android/ ***");
        } else if (!(protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https"))) {
            String async = Lookup.lookup(DefaultSentryClientFactory.ASYNC_OPTION, dsn);
            if (async != null && async.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Sentry Android cannot use synchronous connections, remove '"
                    + DefaultSentryClientFactory.ASYNC_OPTION + "=false' from your options.");
            }

            throw new IllegalArgumentException("Only 'http' or 'https' connections are supported in"
                + " Sentry Android, but received: " + protocol);
        }

        SentryClient sentryClient = super.createSentryClient(dsn);
        sentryClient.addBuilderHelper(new AndroidEventBuilderHelper(ctx));
        SentryUncaughtExceptionHandler.setup();
        return sentryClient;
    }

    @Override
    protected Buffer getBuffer(Dsn dsn) {
        File bufferDir;
        String bufferDirOpt = Lookup.lookup(BUFFER_DIR_OPTION, dsn);
        if (bufferDirOpt != null) {
            bufferDir = new File(bufferDirOpt);
        } else {
            bufferDir = new File(ctx.getCacheDir().getAbsolutePath(), DEFAULT_BUFFER_DIR);
        }

        Log.d(TAG, "Using buffer dir: " + bufferDir.getAbsolutePath());
        return new DiskBuffer(bufferDir, getBufferSize(dsn));
    }

    @Override
    protected ContextManager getContextManager(Dsn dsn) {
        return new SingletonContextManager();
    }

    /**
     * Check whether the application has been granted a certain permission.
     *
     * @param permission Permission as a string
     * @return true if permissions is granted
     */
    private boolean checkPermission(String permission) {
        int res = ctx.checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

}
