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
        this.ctx = ctx;

        Log.d(TAG, "Construction of Android Sentry.");
    }

    @Override
    public SentryClient createSentryClient(Dsn dsn) {
        SentryClient sentryClient = super.createSentryClient(dsn);
        sentryClient.addBuilderHelper(new AndroidEventBuilderHelper(ctx));
        return sentryClient;
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
