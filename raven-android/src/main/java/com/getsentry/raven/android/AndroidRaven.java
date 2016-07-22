package com.getsentry.raven.android;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.getsentry.raven.Raven;
import com.getsentry.raven.RavenFactory;

public class AndroidRaven {

    public static final String TAG = AndroidRaven.class.getName();

    private static RavenWrapper ravenWrapper;

    public static void init(Context context, String dsn) {
        Log.d(TAG, "raven init with context='" + context.toString() + "' and dsn='" + dsn + "'");

        // we always want the app context
        context = context.getApplicationContext();

        Raven raven = RavenFactory.ravenInstance(dsn);
        ravenWrapper = new RavenWrapper(raven, context);
        ravenWrapper.setupUncaughtExceptionHandler();
        // TODO: ravenWrapper.sendAllCachedCapturedEvents();
    }

    // TODO:
    // String permission = android.Manifest.permission.ACCESS_NETWORK_STATE;
    // String permission = android.Manifest.permission.INTERNET;
    private boolean checkPermission(Context context, String permission) {
        int res = context.checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    // TODO:
    private boolean isConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

}
