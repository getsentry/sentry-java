package io.sentry.samples.android;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import io.sentry.Attachment;
import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.SpanStatus;
import io.sentry.UserFeedback;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.samples.android.databinding.ActivityMainBinding;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());

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

    binding.addAttachment.setOnClickListener(
        view -> {
          final File imageFile = getApplicationContext().getFileStreamPath("sentry.png");
          try (final InputStream inputStream =
                  getApplicationContext().getResources().openRawResource(R.raw.sentry);
              FileOutputStream outputStream = new FileOutputStream(imageFile)) {
            final byte[] bytes = new byte[1024];
            while (inputStream.read(bytes) != -1) {
              // To keep the sample code simple this happens on the main thread. Don't do this in a
              // real app.
              outputStream.write(bytes);
            }
            outputStream.flush();

            final Attachment image =
                new Attachment(imageFile.getAbsolutePath(), "sentry.png", "image/png");
            Sentry.configureScope(
                scope -> {
                  scope.addAttachment(image);
                });
          } catch (IOException e) {
            Sentry.captureException(e);
          }

          Sentry.configureScope(
              scope -> {
                String json = "{ \"number\": 10 }";
                Attachment attachment = new Attachment(json.getBytes(), "log.json");
                scope.addAttachment(attachment);

                scope.addAttachment(new Attachment(imageFile.getPath()));
              });

          Sentry.captureMessage("Some message with attachments.");
        });

    binding.captureException.setOnClickListener(
        view -> Sentry.captureException(new Exception(new Exception("Some exception."))));

    binding.breadcrumb.setOnClickListener(
        view -> {
          Sentry.addBreadcrumb("Breadcrumb");
          Sentry.captureMessage("Some message with breadcrumbs.");
        });

    binding.setUser.setOnClickListener(
        view -> {
          User user = new User();
          user.setUsername("username_from_java");
          user.setEmail("email_from_java");
          // Use the client's IP address or enable 'sendDefaultPii' that does it automatically.
          user.setIpAddress("{{auto}}");
          Sentry.setUser(user);
          // pass null to Sentry#setUser to unset the user
          Sentry.captureMessage("Some message with user.");
        });

    binding.localScope.setOnClickListener(
        view -> {
          Sentry.withScope(
              scope -> {
                scope.setTag("local_tag", "value");
                // also possible to set/add more things to the Scope like
                // Scope#setExtra, etc...
                Sentry.captureMessage("Some message with local scope and local_tag.");
              });
          Sentry.captureMessage("Some message without local_tag.");
        });

    binding.nativeCrash.setOnClickListener(view -> NativeSample.crash());

    binding.nativeCapture.setOnClickListener(view -> NativeSample.message());

    binding.anr.setOnClickListener(
        view -> {
          // Try cause ANR by blocking for 10 seconds.
          // By default the SDK sends an event if blocked by at least 5 seconds.
          // Keep clicking on the ANR button till you've gotten the "App. isn''t responding" dialog,
          // then either click on Wait or Close, at this point you should have seen an event on
          // Sentry.
          // NOTE: By default it doesn't raise if the debugger is attached. That can also be
          // configured.
          try {
            Thread.sleep(10000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });

    binding.openSecondActivity.setOnClickListener(
        view -> {
          // finishing so its completely destroyed
          finish();
          startActivity(new Intent(this, SecondActivity.class));
        });

    setContentView(binding.getRoot());
  }

  @Override
  protected void onResume() {
    super.onResume();
    final ISpan span = Sentry.getSpan();
    if (span != null) {
      span.finish(SpanStatus.OK);
    }
  }
}
