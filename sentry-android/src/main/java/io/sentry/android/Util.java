package io.sentry.android;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Sentry Android utility methods.
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
     * Check whether Sentry should attempt to send an event, or just immediately store it.
     *
     * @param ctx Android application context
     * @return true if Sentry should attempt to send an event
     */
    public static boolean shouldAttemptToSend(Context ctx) {
        if (!checkPermission(ctx, android.Manifest.permission.ACCESS_NETWORK_STATE)) {
            // we can't check whether the connection is up, so the
            // best we can do is try
            return true;
        }

        return isConnected(ctx);
    }

}
