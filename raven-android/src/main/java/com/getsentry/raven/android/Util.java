package com.getsentry.raven.android;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.content.Context.ACTIVITY_SERVICE;

/**
 * Raven Android utility methods.
 */
public final class Util {

    private static final String TAG = Util.class.getName();

    /**
     * Hide constructor.
     */
    private Util() {

    }

    /**
     * Check whether the application has been granted a certain permission.
     *
     * @param ctx Android application context
     * @param permission Permission as a string
     * @return true if permissions is granted
     */
    public static boolean checkPermission(Context ctx, String permission) {
        int res = ctx.checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Check whether the application has internet access at a point in time.
     *
     * @param ctx Android application context
     * @return true if the application has internet access
     */
    public static boolean isConnected(Context ctx) {
        ConnectivityManager connectivityManager =
            (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /**
     * Check whether Raven should attempt to send an event, or just immediately store it.
     *
     * @param ctx Android application context
     * @return true if Raven should attempt to send an event
     */
    public static boolean shouldAttemptToSend(Context ctx) {
        if (!checkPermission(ctx, android.Manifest.permission.ACCESS_NETWORK_STATE)) {
            // we can't check whether the connection is up, so the
            // best we can do is try
            return true;
        }

        return isConnected(ctx);
    }

    /**
     * Check whether the application is running in an emulator. http://stackoverflow.com/a/21505193
     *
     * @return true if the application is running in an emulator, false otherwise.
     */
    public static boolean isEmulator() {
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
    public static ActivityManager.MemoryInfo getMemInfo(Context ctx) {
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
    public static String getOrientation(Context ctx) {
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
    public static Float getBatteryLevel(Context ctx) {
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
        return ((float) level / (float) scale) * 100.0f;
        // CHECKSTYLE.ON: MagicNumber
    }

    /**
     * Checks whether or not the device is currently plugged in and charging, or null if unknown.
     *
     * @param ctx Android application context
     * @return whether or not the device is currently plugged in and charging, or null if unknown.
     */
    public static Boolean isCharging(Context ctx) {
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
     * @return the device's current kernel version, as a string (from uname -a).
     */
    public static String getKernelVersion() {
        BufferedReader br = null;
        try {
            Process p = Runtime.getRuntime().exec("uname -a");
            InputStream is = null;
            if (p.waitFor() == 0) {
                is = p.getInputStream();
            } else {
                is = p.getErrorStream();
            }
            br = new BufferedReader(new InputStreamReader(is));
            return br.readLine();
        } catch (Exception e) {
            Log.e(TAG, "Exception while attempting to read kernel information", e);
            return null;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ioe) {
                    Log.e(TAG, "Exception while attempting to read kernel information", ioe);
                }
            }
        }
    }

    /**
     * Attempt to discover if this device is currently rooted. From:
     * https://stackoverflow.com/questions/1101380/determine-if-running-on-a-rooted-device
     *
     * @return true if heuristics show the device is probably rooted, otherwise false
     */
    public static Boolean isRooted() {
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
     * @return the unused amount of internal storage, in bytes.
     */
    public static long getUnusedInternalStorage() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return availableBlocks * blockSize;
    }

    /**
     * Get the total amount of internal storage, in bytes.
     *
     * @return the total amount of internal storage, in bytes.
     */
    public static long getTotalInternalStorage() {
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
    public static Long getUnusedExternalStorage() {
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
    public static Long getTotalExternalStorage() {
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
     * Formats the given Date object into an ISO8601 String. Note that SimpleDateFormat isn't
     * thread safe, and so we build one every time.
     *
     * @param date Date to format as ISO8601
     * @return String representing the provided Date in ISO8601 format
     */
    public static String stringifyDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'").format(date);
    }

}
