package io.sentry.sample;

import android.app.Application;

// import io.sentry.android.core.SentryAndroid;

/** Apps. main Application. */
public class MyApplication extends Application {

  @Override
  public void onCreate() {
    super.onCreate();

    // how to init. Sentry manually
    // SentryAndroid.init(this);
  }
}
