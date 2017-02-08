package com.getsentry.raven.android.event.helper;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.event.helper.EventBuilderHelper;

/**
 * EventBuilderHelper that makes use of Android Context to populate some Event fields.
 */
public class AndroidEventBuilderHelper implements EventBuilderHelper {

    /**
     * Logger tag.
     */
    public static final String TAG = AndroidEventBuilderHelper.class.getName();

    private Context ctx;

    /**
     * Construct given the provided Android {@link Context}.
     *
     * @param ctx Android application context.
     */
    public AndroidEventBuilderHelper(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public void helpBuildingEvent(EventBuilder eventBuilder) {
        eventBuilder.withSdkName(RavenEnvironment.SDK_NAME + ":android");
        try {
            int versionCode = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
            eventBuilder.withRelease(Integer.toString(versionCode));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Couldn't find package version: " + e);
        }
    }

}
