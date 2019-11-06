package io.sentry.sample;

import android.app.Application;
import android.os.StrictMode;
import timber.log.Timber;

// import io.sentry.android.core.SentryAndroid;

/** Apps. main Application. */
public class MyApplication extends Application {

  @Override
  public void onCreate() {
    districtMode();
    super.onCreate();

    Timber.plant(new Timber.DebugTree());

    // how to init. Sentry manually
    // SentryAndroid.init(this);
  }

  private void districtMode() {
    //    https://developer.android.com/reference/android/os/StrictMode
    //    StrictMode is a developer tool which detects things you might be doing by accident and
    //    brings them to your attention so you can fix them.
    if (BuildConfig.DEBUG) {
      StrictMode.setThreadPolicy(
          new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());

      StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
    }

    //    SentryAndroid.init(
    //        this,
    //        options -> {
    //          options.setAnrTimeoutIntervalMills(2000);
    //        });
  }
}
