package io.sentry.samples.android;

import android.app.Application;
import android.os.StrictMode;
import io.sentry.Sentry;
import io.sentry.samples.android.sqlite.SampleDatabases;

/** Apps. main Application. */
public class MyApplication extends Application {

  @Override
  public void onCreate() {
    // Make Session Replay fail fast instead of silently degrading masking when an exception is
    // swallowed (e.g. unsupported/obfuscated Compose internals). This way regressions surface as
    // crashes in our release/obfuscated builds that run on real devices in CI. Only meant for our
    // own sample/UI-test apps, customers should never set this.
    System.setProperty("io.sentry.replay.compose.fail-fast", "true");
    Sentry.startProfiler();
    strictMode();
    super.onCreate();

    SampleDatabases.INSTANCE.warmUp(this);

    // Example how to initialize the SDK manually which allows access to SentryOptions callbacks.
    // Make sure you disable the auto init via manifest meta-data: io.sentry.auto-init=false
    // SentryAndroid.init(
    //    this,
    //    options -> {
    //      /*
    //      use options, for example, to add a beforeSend callback:
    //
    //      options.setBeforeSend((event, hint) -> {
    //        process event
    //      });
    //       */
    //    });
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
