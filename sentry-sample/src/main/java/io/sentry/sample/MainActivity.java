package io.sentry.sample;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import io.sentry.core.Breadcrumb;
import io.sentry.core.Sentry;
import io.sentry.core.SentryLevel;
import io.sentry.core.protocol.User;
import java.util.Collections;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Timber.i("Sentry.isEnabled() = %s", Sentry.isEnabled());

    findViewById(R.id.crash_from_java)
        .setOnClickListener(
            view -> {
              throw new RuntimeException("Uncaught Exception from Java.");
            });

    findViewById(R.id.send_message)
        .setOnClickListener(
            view -> {
              Sentry.captureMessage("Some message.");
            });

    findViewById(R.id.capture_exception)
        .setOnClickListener(
            view -> {
              Sentry.captureException(
                  new Exception(new Exception(new Exception("Some exception."))));
            });

    findViewById(R.id.breadcrumb)
        .setOnClickListener(
            view -> {
              Sentry.configureScope(
                  scope -> {
                    Breadcrumb breadcrumb = new Breadcrumb();
                    breadcrumb.setMessage("Breadcrumb");
                    scope.addBreadcrumb(breadcrumb);
                    scope.setExtra("extra", "extra");
                    scope.setFingerprint(Collections.singletonList("fingerprint"));
                    scope.setLevel(SentryLevel.INFO);
                    scope.setTransaction("transaction");
                    User user = new User();
                    user.setUsername("username");
                    scope.setUser(user);
                    scope.setTag("tag", "tag");
                  });
              Sentry.captureException(new Exception("Some exception with scope."));
            });

    findViewById(R.id.native_crash).setOnClickListener(view -> NativeSample.crash());

    findViewById(R.id.native_capture).setOnClickListener(view -> NativeSample.verificationEvent());

    findViewById(R.id.anr)
        .setOnClickListener(
            view -> {
              // Try cause ANR by blocking for 2.5 seconds.
              // By default the SDK sends an event if blocked by at least 5 seconds.
              // The time was configurable (see manifest) to 1 second for demo purposes.
              // NOTE: By default it doesn't raise if the debugger is attached. That can also be
              // configured.
              try {
                Thread.sleep(2500);
              } catch (InterruptedException e) {
                return;
              }
            });
  }
}
