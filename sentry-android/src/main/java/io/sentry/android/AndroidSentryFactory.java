package io.sentry.android;

import android.content.Context;
import android.util.Log;
import io.sentry.*;
import io.sentry.android.event.helper.AndroidEventBuilderHelper;
import io.sentry.buffer.Buffer;
import io.sentry.buffer.DiskBuffer;
import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
import io.sentry.dsn.Dsn;

import java.io.File;

/**
 * SentryFactory that handles Android-specific construction, like taking advantage
 * of the Android Context instance.
 */
public class AndroidSentryFactory extends DefaultSentryFactory {

    /**
     * Logger tag.
     */
    public static final String TAG = AndroidSentryFactory.class.getName();
    /**
     * Default Buffer directory name.
     */
    private static final String DEFAULT_BUFFER_DIR = "sentry-buffered-events";

    private Context ctx;

    /**
     * Construct an AndroidSentryFactory using the specified Android Context.
     *
     * @param ctx Android Context.
     */
    public AndroidSentryFactory(Context ctx) {
        this.ctx = ctx;

        Log.d(TAG, "Construction of Android Sentry.");
    }

    @Override
    public io.sentry.Sentry createSentryInstance(Dsn dsn) {
        io.sentry.Sentry sentryInstance = super.createSentryInstance(dsn);
        sentryInstance.addBuilderHelper(new AndroidEventBuilderHelper(ctx));
        return sentryInstance;
    }

    @Override
    protected Buffer getBuffer(Dsn dsn) {
        File bufferDir;
        String bufferDirOpt = dsn.getOptions().get(BUFFER_DIR_OPTION);
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

}
