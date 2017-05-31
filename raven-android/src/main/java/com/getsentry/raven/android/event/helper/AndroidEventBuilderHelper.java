package com.getsentry.raven.android.event.helper;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import com.getsentry.raven.android.Util;
import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.event.helper.EventBuilderHelper;
import com.getsentry.raven.event.interfaces.UserInterface;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        PackageInfo packageInfo = getPackageInfo(ctx);
        if (packageInfo != null) {
            eventBuilder.withRelease(packageInfo.versionName);
        }

        String androidId = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId != null && !androidId.trim().equals("")) {
            UserInterface userInterface = new UserInterface("android:" + androidId, null, null, null);
            // set user interface but *don't* replace if it's already there
            eventBuilder.withSentryInterface(userInterface, false);
        }

        eventBuilder.withContexts(getContexts());
    }

    private Map<String, Map<String, Object>> getContexts() {
        Map<String, Map<String, Object>> contexts = new HashMap<>();
        Map<String, Object> deviceMap = new HashMap<>();
        Map<String, Object> osMap     = new HashMap<>();
        Map<String, Object> appMap    = new HashMap<>();
        contexts.put("os",     osMap);
        contexts.put("device", deviceMap);
        contexts.put("app", appMap);

        // Device
        deviceMap.put("manufacturer",          Build.MANUFACTURER);
        deviceMap.put("brand",                 Build.BRAND);
        deviceMap.put("model",                 Build.MODEL);
        deviceMap.put("family",                getFamily());
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

        // App
        PackageInfo packageInfo = getPackageInfo(ctx);
        if (packageInfo != null) {
            appMap.put("app_version", packageInfo.versionName);
            appMap.put("app_build", packageInfo.versionCode);
            appMap.put("app_identifier", packageInfo.packageName);
        }

        appMap.put("app_name", getApplicationName(ctx));
        appMap.put("app_start_time", stringifyDate(new Date()));

        return contexts;
    }

    /**
     * Return the Application's PackageInfo if possible, or null.
     *
     * @param ctx Android application context
     * @return the Application's PackageInfo if possible, or null
     */
    private static PackageInfo getPackageInfo(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error getting package info.", e);
            return null;
        }
    }

    /**
     * Fake the device family by using the first word in the Build.MODEL. Works
     * well in most cases... "Nexus 6P" -> "Nexus", "Galaxy S7" -> "Galaxy".
     *
     * @return family name of the device, as best we can tell
     */
    private static String getFamily() {
        try {
            return Build.MODEL.split(" ")[0];
        } catch (Exception e) {
            Log.e(TAG, "Error getting device family.", e);
            return null;
        }
    }

    /**
     * Check whether the application is running in an emulator. http://stackoverflow.com/a/21505193
     *
     * @return true if the application is running in an emulator, false otherwise
     */
    private static Boolean isEmulator() {
        try {
            return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
        } catch (Exception e) {
            Log.e(TAG, "Error checking whether application is running in an emulator.", e);
            return null;
        }
    }

    /**
     * Get MemoryInfo object representing the memory state of the application.
     *
     * @param ctx Android application context
     * @return MemoryInfo object representing the memory state of the application
     */
    private static ActivityManager.MemoryInfo getMemInfo(Context ctx) {
        try {
            ActivityManager actManager = (ActivityManager) ctx.getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            actManager.getMemoryInfo(memInfo);
            return memInfo;
        } catch (Exception e) {
            Log.e(TAG, "Error getting MemoryInfo.", e);
            return null;
        }
    }

    /**
     * Get the device's current screen orientation.
     *
     * @param ctx Android application context
     * @return the device's current screen orientation, or null if unknown
     */
    private static String getOrientation(Context ctx) {
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error getting device orientation.", e);
            return null;
        }
    }

    /**
     * Get the device's current battery level (as a percentage of total).
     *
     * @param ctx Android application context
     * @return the device's current battery level (as a percentage of total), or null if unknown
     */
    private static Float getBatteryLevel(Context ctx) {
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error getting device battery level.", e);
            return null;
        }
    }

    /**
     * Checks whether or not the device is currently plugged in and charging, or null if unknown.
     *
     * @param ctx Android application context
     * @return whether or not the device is currently plugged in and charging, or null if unknown
     */
    private static Boolean isCharging(Context ctx) {
        try {
            Intent intent = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent == null) {
                return null;
            }

            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
        } catch (Exception e) {
            Log.e(TAG, "Error getting device charging state.", e);
            return null;
        }
    }

    /**
     * Reads a line from the specified file.
     * @param filename the file to read from
     * @return the first line, if any.
     * @throws IOException if the file couldn't be read
     */
    private static String readLine(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        try {
            return reader.readLine();
        } finally {
            reader.close();
        }
    }

    /**
     * Get the device's current kernel version, as a string (from /proc/version).
     *
     * @return the device's current kernel version, as a string (from /proc/version)
     */
    private static String getKernelVersion() {
        try {
            return formatKernelVersion(readLine("/proc/version"));
        } catch (Exception e) {
            Log.e(TAG, "Exception while attempting to read kernel information", e);
        }

        return null;
    }

    private static String formatKernelVersion(String rawKernelVersion) {
        // Example (see tests for more):
        // Linux version 3.0.31-g6fb96c9 (android-build@xxx.xxx.xxx.xxx.com) \
        //     (gcc version 4.6.x-xxx 20120106 (prerelease) (GCC) ) #1 SMP PREEMPT \
        //     Thu Jun 28 11:02:39 PDT 2012

        final String procVersionRegex =
                "Linux version (\\S+) " + /* group 1: "3.0.31-g6fb96c9" */
                "\\((\\S+?)\\) " +        /* group 2: "x@y.com" (kernel builder) */
                "(?:\\(gcc.+? \\)) " +    /* ignore: GCC version information */
                "(#\\d+) " +              /* group 3: "#1" */
                "(?:.*?)?" +              /* ignore: optional SMP, PREEMPT, and any CONFIG_FLAGS */
                "((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)"; /* group 4: "Thu Jun 28 11:02:39 PDT 2012" */

        // CHECKSTYLE.OFF: MagicNumber
        final int procVersionGroupKernelVersion = 1;
        final int procVersionGroupBuilderName = 2;
        final int procVersionGroupBuildNumber = 3;
        final int procVersionGroupBuildDate = 4;
        final int procVersionGroupCount = 4;
        // CHECKSTYLE.ON: MagicNumber

        Matcher m = Pattern.compile(procVersionRegex).matcher(rawKernelVersion);
        if (!m.matches()) {
            Log.e(TAG, "Regex did not match on /proc/version: " + rawKernelVersion);
            return null;
        } else if (m.groupCount() < procVersionGroupCount) {
            Log.e(TAG, "Regex match on /proc/version only returned " + m.groupCount()
                    + " groups");
            return null;
        }
        return m.group(procVersionGroupKernelVersion) + "\n" + // 3.0.31-g6fb96c9
                m.group(procVersionGroupBuilderName) + " " + m.group(procVersionGroupBuildNumber) + "\n" + // x@y.com #1
                m.group(procVersionGroupBuildDate); // Thu Jun 28 11:02:39 PDT 2012
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

        for (String probableRootPath : probableRootPaths) {
            try {
                if (new File(probableRootPath).exists()) {
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception while attempting to detect whether the device is rooted", e);
            }
        }

        return false;
    }

    private static boolean isExternalStorageMounted() {
        return Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)
            && !Environment.isExternalStorageEmulated();
    }

    /**
     * Get the unused amount of internal storage, in bytes.
     *
     * @return the unused amount of internal storage, in bytes
     */
    private static Long getUnusedInternalStorage() {
        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            return availableBlocks * blockSize;
        } catch (Exception e) {
            Log.e(TAG, "Error getting unused internal storage amount.", e);
            return null;
        }
    }

    /**
     * Get the total amount of internal storage, in bytes.
     *
     * @return the total amount of internal storage, in bytes
     */
    private static Long getTotalInternalStorage() {
        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long totalBlocks = stat.getBlockCount();
            return totalBlocks * blockSize;
        } catch (Exception e) {
            Log.e(TAG, "Error getting total internal storage amount.", e);
            return null;
        }
    }

    /**
     * Get the unused amount of external storage, in bytes, or null if no external storage
     * is mounted.
     *
     * @return the unused amount of external storage, in bytes, or null if no external storage
     * is mounted
     */
    private static Long getUnusedExternalStorage() {
        try {
            if (isExternalStorageMounted()) {
                File path = Environment.getExternalStorageDirectory();
                StatFs stat = new StatFs(path.getPath());
                long blockSize = stat.getBlockSize();
                long availableBlocks = stat.getAvailableBlocks();
                return availableBlocks * blockSize;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting unused external storage amount.", e);
        }

        return null;
    }

    /**
     * Get the total amount of external storage, in bytes, or null if no external storage
     * is mounted.
     *
     * @return the total amount of external storage, in bytes, or null if no external storage
     * is mounted
     */
    private static Long getTotalExternalStorage() {
        try {
            if (isExternalStorageMounted()) {
                File path = Environment.getExternalStorageDirectory();
                StatFs stat = new StatFs(path.getPath());
                long blockSize = stat.getBlockSize();
                long totalBlocks = stat.getBlockCount();
                return totalBlocks * blockSize;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting total external storage amount.", e);
        }

        return null;
    }

    /**
     * Get the DisplayMetrics object for the current application.
     *
     * @param ctx Android application context
     * @return the DisplayMetrics object for the current application
     */
    private static DisplayMetrics getDisplayMetrics(Context ctx) {
        try {
            return ctx.getResources().getDisplayMetrics();
        } catch (Exception e) {
            Log.e(TAG, "Error getting DisplayMetrics.", e);
            return null;
        }
    }

    /**
     * Formats the given Date object into an ISO8601 String. Note that SimpleDateFormat isn't
     * thread safe, and so we build one every time.
     *
     * @param date Date to format as ISO8601
     * @return String representing the provided Date in ISO8601 format
     */
    private static String stringifyDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(date);
    }

    /**
     * Get the human-facing Application name.
     *
     * @param ctx Android application context
     * @return Application name
     */
    private static String getApplicationName(Context ctx) {
        try {
            ApplicationInfo applicationInfo = ctx.getApplicationInfo();
            int stringId = applicationInfo.labelRes;
            if (stringId == 0) {
                if (applicationInfo.nonLocalizedLabel != null) {
                    return applicationInfo.nonLocalizedLabel.toString();
                }
            } else {
                return ctx.getString(stringId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting application name.", e);
        }

        return null;
    }

}
