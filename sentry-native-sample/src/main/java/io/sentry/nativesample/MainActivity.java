package io.sentry.nativesample;

import android.os.Bundle;
import android.os.StrictMode;

import androidx.appcompat.app.AppCompatActivity;
import io.sentry.core.Sentry;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    districtMode();
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Timber.plant(new Timber.DebugTree());

    Timber.i("Sentry.isEnabled() = %s", Sentry.isEnabled());

    findViewById(R.id.native_capture).setOnClickListener(view -> NativeSample.message());
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
  }
}
