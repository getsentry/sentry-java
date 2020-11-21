package io.sentry.sample;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import io.sentry.*;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.sample.databinding.ActivityMainBinding;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    SentryTransaction activityTransaction = Sentry.startTransaction("MainActivity.onCreate");
    Span innerSpan = activityTransaction.startChild();
    innerSpan.setOperation("view");
    innerSpan.setDescription("super.onCreate");
    super.onCreate(savedInstanceState);
    innerSpan.setStatus(SpanStatus.OK);
    innerSpan.finish();

    innerSpan = activityTransaction.startChild();
    innerSpan.setOperation("view");
    innerSpan.setDescription("inflating.binding");
    ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
    innerSpan.setStatus(SpanStatus.OK);
    innerSpan.finish();

    innerSpan = activityTransaction.startChild();
    innerSpan.setOperation("view");
    innerSpan.setDescription("setOnClickListener");
    binding.crashFromJava.setOnClickListener(
        view -> {
          throw new RuntimeException("Uncaught Exception from Java.");
        });

    binding.sendMessage.setOnClickListener(view -> Sentry.captureMessage("Some message."));

    binding.sendUserFeedback.setOnClickListener(
        view -> {
          SentryId sentryId = Sentry.captureException(new Exception("I have feedback"));

          UserFeedback userFeedback = new UserFeedback(sentryId);
          userFeedback.setComments("It broke on Android. I don't know why, but this happens.");
          userFeedback.setEmail("john@me.com");
          userFeedback.setName("John Me");
          Sentry.captureUserFeedback(userFeedback);
        });

    binding.captureException.setOnClickListener(
        view ->
            Sentry.captureException(
                new Exception(new Exception(new Exception("Some exception.")))));

    binding.breadcrumb.setOnClickListener(
        view -> {
          Sentry.addBreadcrumb("Breadcrumb");
          Sentry.setExtra("extra", "extra");
          Sentry.setFingerprint(Collections.singletonList("fingerprint"));
          Sentry.setTransaction("transaction");
          Sentry.captureException(new Exception("Some exception with scope."));
        });

    binding.unsetUser.setOnClickListener(
        view -> {
          Sentry.setTag("user_set", "null");
          Sentry.setUser(null);
        });

    binding.setUser.setOnClickListener(
        view -> {
          Sentry.setTag("user_set", "instance");
          User user = new User();
          user.setUsername("username_from_java");
          // works with some null properties?
          // user.setId("id_from_java");
          user.setEmail("email_from_java");
          // Use the client's IP address
          user.setIpAddress("{{auto}}");
          Sentry.setUser(user);
        });

    binding.nativeCrash.setOnClickListener(view -> NativeSample.crash());

    binding.nativeCapture.setOnClickListener(view -> NativeSample.message());

    binding.anr.setOnClickListener(
        view -> {
          SentryTransaction anrTransaction = Sentry.startTransaction("MainActivity.ANR");
          Span anrSpan = anrTransaction.startChild();
          anrSpan.setOperation("button");
          anrSpan.setDescription("ANR demo button");
          // Try cause ANR by blocking for 2.5 seconds.
          // By default the SDK sends an event if blocked by at least 4 seconds.
          // The time was configurable (see manifest) to 1 second for demo purposes.
          // NOTE: By default it doesn't raise if the debugger is attached. That can also be
          // configured.
          try {
            Thread.sleep(2500);
            anrSpan.setStatus(SpanStatus.OK);
          } catch (InterruptedException e) {
            anrSpan.setStatus(SpanStatus.ABORTED);
            Thread.currentThread().interrupt();
          }
          finally {
            anrSpan.finish();
            anrTransaction.finish();
            Sentry.captureTransaction(anrTransaction, null);
          }
        });
    innerSpan.setStatus(SpanStatus.OK);
    innerSpan.finish();

    innerSpan = activityTransaction.startChild();
    innerSpan.setOperation("view");
    innerSpan.setDescription("setContentView");
    setContentView(binding.getRoot());
    innerSpan.setStatus(SpanStatus.OK);
    innerSpan.finish();

    activityTransaction.finish();
    Sentry.captureTransaction(activityTransaction, null);
  }
}
