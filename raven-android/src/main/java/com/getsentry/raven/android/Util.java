package com.getsentry.raven.android;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Raven Android utility methods.
 */
public final class Util {

    /**
     * Hide constructor.
     */
    private Util() {

    }

    /**
     * Check whether the application has been granted a certain permission.
     *
     * @param ctx Android application ctx
     * @param permission Permission as a string
     * @return true if permissions is granted
     */
    public static boolean checkPermission(Context ctx, String permission) {
        int res = ctx.checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

}
