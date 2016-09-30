package com.getsentry.raven.android;

import android.content.Context;
import com.getsentry.raven.DefaultRavenFactory;
import com.getsentry.raven.buffer.Buffer;
import com.getsentry.raven.buffer.DiskBuffer;
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
    private static final String DEFAULT_BUFFER_DIR = "raven_unsent_events";

    private Context ctx;

    /**
     * Construct an AndroidRavenFactory using the specified Android Context.
     *
     * @param ctx Android Context.
     */
    public AndroidRavenFactory(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    protected Buffer getBuffer(Dsn dsn) {
        File bufferDir;
        if (dsn.getOptions().get(BUFFER_DIR_OPTION) != null) {
            bufferDir = new File(dsn.getOptions().get(BUFFER_DIR_OPTION));
        } else {
            bufferDir = new File(ctx.getCacheDir().getAbsolutePath(), DEFAULT_BUFFER_DIR);
        }

        return new DiskBuffer(bufferDir, getBufferSize(dsn));
    }

}
