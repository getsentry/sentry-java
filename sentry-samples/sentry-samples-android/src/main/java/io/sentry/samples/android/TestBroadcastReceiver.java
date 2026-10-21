package io.sentry.samples.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * A manifest-declared broadcast receiver for testing standalone app starts.
 *
 * <p>Test with:
 *
 * <pre>{@code
 * adb shell am force-stop io.sentry.samples.android && \
 * adb shell am broadcast -a io.sentry.samples.android.TEST_BROADCAST \
 *   -n io.sentry.samples.android/.TestBroadcastReceiver
 * }</pre>
 */
public class TestBroadcastReceiver extends BroadcastReceiver {
  private static final String TAG = "SentryAppStart";

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(TAG, "TestBroadcastReceiver.onReceive() called - no activity will launch");
  }
}
