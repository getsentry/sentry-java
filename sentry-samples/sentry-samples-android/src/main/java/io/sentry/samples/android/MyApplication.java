package io.sentry.samples.android;

import android.app.Application;
import android.os.StrictMode;
import io.sentry.Sentry;
import io.sentry.android.core.SentryAndroid;

/** Apps. main Application. */
public class MyApplication extends Application {

  @Override
  public void onCreate() {
    Sentry.startProfiler();
    strictMode();
    super.onCreate();

    // Enable shake gesture to open feedback form (for testing)
    SentryAndroid.init(
        this,
        options -> {
          options.getFeedbackOptions().setUseShakeGesture(true);
        });
  }

  private void strictMode() {
    //    https://developer.android.com/reference/android/os/StrictMode
    //    StrictMode is a developer tool which detects things you might be doing by accident and
    //    brings them to your attention so you can fix them.
    if (BuildConfig.DEBUG) {
      StrictMode.setThreadPolicy(
          new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());

      StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
    }
  }
}
