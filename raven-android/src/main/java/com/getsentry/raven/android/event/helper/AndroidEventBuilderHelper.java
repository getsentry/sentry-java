package com.getsentry.raven.android.event.helper;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import com.getsentry.raven.android.Util;
import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.event.helper.EventBuilderHelper;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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

        eventBuilder.withContexts(getContexts());
    }

    private Map<String, Map<String, Object>> getContexts() {
        Map<String, Map<String, Object>> contexts = new HashMap<>();
        Map<String, Object> deviceMap = new HashMap<>();
        Map<String, Object> osMap = new HashMap<>();
        contexts.put("os", osMap);
        contexts.put("device", deviceMap);

        deviceMap.put("family", "Android");
        deviceMap.put("manufacturer", Build.MANUFACTURER);
        deviceMap.put("brand", Build.BRAND);
        deviceMap.put("model", Build.MODEL);
        deviceMap.put("model_id", Build.ID);
        deviceMap.put("battery_level", Util.getBatteryLevel(ctx));
        deviceMap.put("orientation", Util.getOrientation(ctx));
        deviceMap.put("simulator", Util.isEmulator());
        deviceMap.put("arch", Build.CPU_ABI);
        deviceMap.put("storage_size", Util.getTotalInternalStorage());
        deviceMap.put("free_storage", Util.getUnusedInternalStorage());
        deviceMap.put("external_storage_size", Util.getTotalExternalStorage());
        deviceMap.put("external_free_storage", Util.getUnusedExternalStorage());
        deviceMap.put("charging", Util.isCharging(ctx));
        deviceMap.put("time", Util.stringifyDate(new Date()));
        deviceMap.put("online", Util.isConnected(ctx));
        // screen_resolution
        // screen_dpi
        // screen_density
        // running_time

        ActivityManager.MemoryInfo memInfo = Util.getMemInfo(ctx);
        // deviceMap.put("usable_memory", ""); // Android doesn't seem to provide this as its own value, but iOS does?
        deviceMap.put("free_memory", memInfo.availMem);
        deviceMap.put("memory_size", memInfo.totalMem);
        deviceMap.put("low_memory", memInfo.lowMemory);

        osMap.put("name", "Android");
        osMap.put("version", Build.VERSION.RELEASE);
        osMap.put("build", Build.DISPLAY);
        osMap.put("kernel_version", Util.getKernelVersion());
        osMap.put("rooted", Util.isRooted());

        return contexts;
    }

}
