package io.sentry.sample;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import io.sentry.core.Sentry;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Timber.plant(new Timber.DebugTree());

    Timber.i("Sentry.isEnabled() = %s", Sentry.isEnabled());

    findViewById(R.id.crash)
        .setOnClickListener(
            view -> {
              throw new RuntimeException("Some runtime exception.");
            });

    findViewById(R.id.send_message)
        .setOnClickListener(
            view -> {
              Sentry.captureMessage("Some message.");
            });

    findViewById(R.id.capture_exception)
        .setOnClickListener(
            view -> {
              Sentry.captureException(new Exception("Some exception."));
            });
  }
}
