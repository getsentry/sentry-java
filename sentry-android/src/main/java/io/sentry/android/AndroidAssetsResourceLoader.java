package io.sentry.android;

import android.content.Context;
import android.util.Log;
import io.sentry.config.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

/**
 * ResourceLoader specific for Android that loads files from assets.
 */
@SuppressWarnings("unused")
public class AndroidAssetsResourceLoader implements ResourceLoader {

    /**
     * Logger tag.
     */
    private static final String TAG = AndroidAssetsResourceLoader.class.getName();

    private Context context;

    /**
     * Construct an AndroidAssetsResourceLoader using an Application Context from the provided context
     * or the latter if the application context is null.
     *
     * @param context Android context
     */
    public AndroidAssetsResourceLoader(Context context) {
        this.context = context.getApplicationContext();

        if (this.context == null) {
            this.context = context;
        }
    }

    @Override
    public InputStream getInputStream(String filepath) {
        try {
            return context.getAssets().open(filepath);
        } catch (IOException e) {
            Log.e(TAG, "Cannot open stream from file: " + filepath, e);
        }

        return null;
    }
}
