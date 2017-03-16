package com.getsentry.raven.android;

import android.content.Context;
import android.util.Log;
import com.getsentry.raven.*;
import com.getsentry.raven.android.event.helper.AndroidEventBuilderHelper;
import com.getsentry.raven.buffer.Buffer;
import com.getsentry.raven.buffer.DiskBuffer;
import com.getsentry.raven.context.ContextManager;
import com.getsentry.raven.context.SingletonContextManager;
import com.getsentry.raven.dsn.Dsn;

import java.io.File;

/**
 * RavenFactory that handles Android-specific construction, like taking advantage
 * of the Android Context instance.
 */
public class AndroidRavenFactory extends DefaultRavenFactory {

    /**
     * Logger tag.
     */
    public static final String TAG = AndroidRavenFactory.class.getName();
    /**
     * Default Buffer directory name.
     */
    private static final String DEFAULT_BUFFER_DIR = "raven-buffered-events";

    private Context ctx;

    /**
     * Construct an AndroidRavenFactory using the specified Android Context.
     *
     * @param ctx Android Context.
     */
    public AndroidRavenFactory(Context ctx) {
        this.ctx = ctx;

        Log.d(TAG, "Construction of Android Raven.");
    }

    @Override
    public com.getsentry.raven.Raven createRavenInstance(Dsn dsn) {
        com.getsentry.raven.Raven ravenInstance = super.createRavenInstance(dsn);
        ravenInstance.addBuilderHelper(new AndroidEventBuilderHelper(ctx));
        return ravenInstance;
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
