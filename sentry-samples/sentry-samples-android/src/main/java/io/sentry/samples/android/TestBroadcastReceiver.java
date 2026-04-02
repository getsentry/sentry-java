package io.sentry.samples.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * A manifest-declared broadcast receiver for testing app start importance.
 *
 * <p>When this receiver triggers a cold start (process was dead), Application.onCreate() runs
 * first. We can then check if importance == IMPORTANCE_FOREGROUND even though no activity will
 * launch.
 *
 * <p>Test with: adb shell am force-stop io.sentry.samples.android adb shell am broadcast -a
 * io.sentry.samples.android.TEST_BROADCAST -n
 * io.sentry.samples.android/.TestBroadcastReceiver
 */
public class TestBroadcastReceiver extends BroadcastReceiver {
  private static final String TAG = "SentryAppStart";

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(TAG, "TestBroadcastReceiver.onReceive() called - no activity will launch");
  }
}
