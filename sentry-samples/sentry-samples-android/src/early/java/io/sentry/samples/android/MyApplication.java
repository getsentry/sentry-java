package io.sentry.samples.android;

import android.app.Application;
import android.content.Context;
import android.os.StrictMode;

import io.sentry.android.core.ActivityFramesTracker;
import io.sentry.android.core.ActivityLifecycleIntegration;
import io.sentry.android.core.BuildInfoProvider;
import io.sentry.android.core.LoadClass;
import io.sentry.android.core.SentryAndroid;
import io.sentry.android.fragment.FragmentLifecycleIntegration;
import io.sentry.android.timber.SentryTimberIntegration;

/** Apps. main Application. */
public class MyApplication extends Application {

  @Override
  protected void attachBaseContext(Context base) {
    // Example how to initialize the SDK manually which allows access to SentryOptions callbacks.
    // Make sure you disable the auto init via manifest meta-data: io.sentry.auto-init=false
    SentryAndroid.init(
      base,
      options -> {
        options.addIntegration(new ActivityLifecycleIntegration(
          MyApplication.this,
          new BuildInfoProvider(),
          new ActivityFramesTracker(new LoadClass())
        ));
        options.addIntegration(new FragmentLifecycleIntegration(MyApplication.this, true, true));
        options.addIntegration(new SentryTimberIntegration());
      });

    super.attachBaseContext(base);
  }

  @Override
  public void onCreate() {
    strictMode();
    super.onCreate();
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
