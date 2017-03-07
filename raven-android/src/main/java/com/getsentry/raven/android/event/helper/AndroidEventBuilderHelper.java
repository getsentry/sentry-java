package com.getsentry.raven.android.event.helper;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.util.Log;
import com.getsentry.raven.android.Util;
import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.event.helper.EventBuilderHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static android.content.Context.ACTIVITY_SERVICE;

/**
 * EventBuilderHelper that makes use of Android Context to populate some Event fields.
 */
public class AndroidEventBuilderHelper implements EventBuilderHelper {

    /**
     * Logger tag.
     */
    public static final String TAG = AndroidEventBuilderHelper.class.getName();

    private static final Boolean IS_EMULATOR = isEmulator();
    private static final String KERNEL_VERSION = getKernelVersion();

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
        Map<String, Object> osMap     = new HashMap<>();
        contexts.put("os",     osMap);
        contexts.put("device", deviceMap);

        // Device
        deviceMap.put("family",                "Android");
        deviceMap.put("manufacturer",          Build.MANUFACTURER);
        deviceMap.put("brand",                 Build.BRAND);
        deviceMap.put("model",                 Build.MODEL);
        deviceMap.put("model_id",              Build.ID);
        deviceMap.put("battery_level",         getBatteryLevel(ctx));
        deviceMap.put("orientation",           getOrientation(ctx));
        deviceMap.put("simulator",             IS_EMULATOR);
        deviceMap.put("arch",                  Build.CPU_ABI);
        deviceMap.put("storage_size",          getTotalInternalStorage());
        deviceMap.put("free_storage",          getUnusedInternalStorage());
        deviceMap.put("external_storage_size", getTotalExternalStorage());
        deviceMap.put("external_free_storage", getUnusedExternalStorage());
        deviceMap.put("charging",              isCharging(ctx));
        deviceMap.put("time",                  stringifyDate(new Date()));
        deviceMap.put("online",                Util.isConnected(ctx));

        DisplayMetrics displayMetrics = getDisplayMetrics(ctx);
        if (displayMetrics != null) {
            int largestSide   = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
            int smallestSide  = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);
            String resolution = Integer.toString(largestSide) + "x" + Integer.toString(smallestSide);
            deviceMap.put("screen_resolution", resolution);
            deviceMap.put("screen_density",    displayMetrics.density);
            deviceMap.put("screen_dpi",        displayMetrics.densityDpi);
        }

        ActivityManager.MemoryInfo memInfo = getMemInfo(ctx);
        if (memInfo != null) {
            deviceMap.put("free_memory", memInfo.availMem);
            deviceMap.put("memory_size", memInfo.totalMem);
            deviceMap.put("low_memory",  memInfo.lowMemory);
        }

        // Operating System
        osMap.put("name",           "Android");
        osMap.put("version",        Build.VERSION.RELEASE);
        osMap.put("build",          Build.DISPLAY);
        osMap.put("kernel_version", KERNEL_VERSION);
        osMap.put("rooted",         isRooted());

        return contexts;
    }

    /**
     * Check whether the application is running in an emulator. http://stackoverflow.com/a/21505193
     *
     * @return true if the application is running in an emulator, false otherwise
     */
    private static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || "google_sdk".equals(Build.PRODUCT);
    }

    /**
     * Get MemoryInfo object representing the memory state of the application.
     *
     * @param ctx Android application context
     * @return MemoryInfo object representing the memory state of the application
     */
    private static ActivityManager.MemoryInfo getMemInfo(Context ctx) {
        ActivityManager actManager = (ActivityManager) ctx.getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        return memInfo;
    }

    /**
     * Get the device's current screen orientation.
     *
     * @param ctx Android application context
     * @return the device's current screen orientation, or null if unknown
     */
    private static String getOrientation(Context ctx) {
        String o;
        switch (ctx.getResources().getConfiguration().orientation) {
            case android.content.res.Configuration.ORIENTATION_LANDSCAPE:
                o = "landscape";
                break;
            case android.content.res.Configuration.ORIENTATION_PORTRAIT:
                o = "portrait";
                break;
            default:
                o = null;
                break;
        }
        return o;
    }

    /**
     * Get the device's current battery level (as a percentage of total).
     *
     * @param ctx Android application context
     * @return the device's current battery level (as a percentage of total), or null if unknown
     */
    private static Float getBatteryLevel(Context ctx) {
        Intent intent = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) {
            return null;
        }

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        if (level == -1 || scale == -1) {
            return null;
        }

        // CHECKSTYLE.OFF: MagicNumber
        float percentMultiplier = 100.0f;
        // CHECKSTYLE.ON: MagicNumber

        return ((float) level / (float) scale) * percentMultiplier;
    }

    /**
     * Checks whether or not the device is currently plugged in and charging, or null if unknown.
     *
     * @param ctx Android application context
     * @return whether or not the device is currently plugged in and charging, or null if unknown
     */
    private static Boolean isCharging(Context ctx) {
        Intent intent = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) {
            return null;
        }

        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    }

    /**
     * Get the device's current kernel version, as a string (from uname -a).
     *
     * @return the device's current kernel version, as a string (from uname -a)
     */
    private static String getKernelVersion() {
        String errorMsg = "Exception while attempting to read kernel information";
        BufferedReader br = null;
        try {
            Process p = Runtime.getRuntime().exec("uname -a");
            if (p.waitFor() == 0) {
                br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                return br.readLine();
            }
        } catch (Exception e) {
            Log.e(TAG, errorMsg, e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ioe) {
                    Log.e(TAG, errorMsg, ioe);
                }
            }
        }

        return null;
    }

    /**
     * Attempt to discover if this device is currently rooted. From:
     * https://stackoverflow.com/questions/1101380/determine-if-running-on-a-rooted-device
     *
     * @return true if heuristics show the device is probably rooted, otherwise false
     */
    private static Boolean isRooted() {
        if (android.os.Build.TAGS != null && android.os.Build.TAGS.contains("test-keys")) {
            return true;
        }

        String[] probableRootPaths = {
            "/data/local/bin/su",
            "/data/local/su",
            "/data/local/xbin/su",
            "/sbin/su",
            "/su/bin",
            "/su/bin/su",
            "/system/app/SuperSU",
            "/system/app/SuperSU.apk",
            "/system/app/Superuser",
            "/system/app/Superuser.apk",
            "/system/bin/failsafe/su",
            "/system/bin/su",
            "/system/sd/xbin/su",
            "/system/xbin/daemonsu",
            "/system/xbin/su"
        };

        try {
            for (String probableRootPath : probableRootPaths) {
                if (new File(probableRootPath).exists()) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while attempting to detect whether the device is rooted", e);
        }
        return false;
    }

    private static boolean isExternalStorageMounted() {
        return Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED);
    }

    /**
     * Get the unused amount of internal storage, in bytes.
     *
     * @return the unused amount of internal storage, in bytes
     */
    private static long getUnusedInternalStorage() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return availableBlocks * blockSize;
    }

    /**
     * Get the total amount of internal storage, in bytes.
     *
     * @return the total amount of internal storage, in bytes
     */
    private static long getTotalInternalStorage() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long totalBlocks = stat.getBlockCount();
        return totalBlocks * blockSize;
    }

    /**
     * Get the unused amount of external storage, in bytes, or null if no external storage
     * is mounted.
     *
     * @return the unused amount of external storage, in bytes, or null if no external storage
     * is mounted
     */
    private static Long getUnusedExternalStorage() {
        if (isExternalStorageMounted()) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            return availableBlocks * blockSize;
        } else {
            return null;
        }
    }

    /**
     * Get the total amount of external storage, in bytes, or null if no external storage
     * is mounted.
     *
     * @return the total amount of external storage, in bytes, or null if no external storage
     * is mounted
     */
    private static Long getTotalExternalStorage() {
        if (isExternalStorageMounted()) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long totalBlocks = stat.getBlockCount();
            return totalBlocks * blockSize;
        } else {
            return null;
        }
    }

    /**
     * Get the DisplayMetrics object for the current application.
     *
     * @param ctx Android application context
     * @return the DisplayMetrics object for the current application
     */
    private static DisplayMetrics getDisplayMetrics(Context ctx) {
        Resources resources = ctx.getResources();
        if (resources == null) {
            return null;
        }
        return resources.getDisplayMetrics();
    }

    /**
     * Formats the given Date object into an ISO8601 String. Note that SimpleDateFormat isn't
     * thread safe, and so we build one every time.
     *
     * @param date Date to format as ISO8601
     * @return String representing the provided Date in ISO8601 format
     */
    private static String stringifyDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'").format(date);
    }


}
