package io.sentry.samples.android;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import io.sentry.Attachment;
import io.sentry.ISpan;
import io.sentry.MeasurementUnit;
import io.sentry.Sentry;
import io.sentry.instrumentation.file.SentryFileOutputStream;
import io.sentry.protocol.Feedback;
import io.sentry.protocol.User;
import io.sentry.samples.android.compose.ComposeActivity;
import io.sentry.samples.android.databinding.ActivityMainBinding;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

  private int crashCount = 0;
  private int screenLoadCount = 0;

  final Object mutex = new Object();

  @Override
  @SuppressWarnings("deprecation")
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    SharedState.INSTANCE.setOrientationChange(
        getIntent().getBooleanExtra("isOrientationChange", false));
    final ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());

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
    } catch (IOException e) {
      Sentry.captureException(e);
    }

    final Attachment image = new Attachment(imageFile.getAbsolutePath(), "sentry.png", "image/png");
    Sentry.configureScope(
        scope -> {
          scope.addAttachment(image);
        });

    binding.crashFromJava.setOnClickListener(
        view -> {
          throw new RuntimeException("Uncaught Exception from Java.");
        });

    binding.sendMessage.setOnClickListener(view -> Sentry.captureMessage("Some message."));

    binding.sendUserFeedback.setOnClickListener(
        view -> {
          Feedback feedback =
              new Feedback("It broke on Android. I don't know why, but this happens.");
          feedback.setContactEmail("john@me.com");
          feedback.setName("John Me");
          Sentry.captureFeedback(feedback);
        });

    binding.addAttachment.setOnClickListener(
        view -> {
          String fileName = Calendar.getInstance().getTimeInMillis() + "_file.txt";
          File file = getApplication().getFileStreamPath(fileName);
          try (final FileOutputStream fos =
              SentryFileOutputStream.Factory.create(new FileOutputStream(file), file)) {
            FileChannel channel = fos.getChannel();
            channel.write(java.nio.ByteBuffer.wrap("Hello, World!".getBytes()));
          } catch (IOException e) {
            Sentry.captureException(e);
          }

          Sentry.configureScope(
              scope -> {
                String json = "{ \"number\": 10 }";
                Attachment attachment = new Attachment(json.getBytes(), "log.json");
                scope.addAttachment(attachment);
                scope.addAttachment(new Attachment(file.getPath()));
              });
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

    binding.outOfMemory.setOnClickListener(
        view -> {
          final CountDownLatch latch = new CountDownLatch(1);
          for (int i = 0; i < 20; i++) {
            new Thread(
                    () -> {
                      final List<String> data = new ArrayList<>();
                      try {
                        latch.await();
                        for (int j = 0; j < 1_000_000; j++) {
                          data.add(new String(new byte[1024 * 8]));
                        }
                      } catch (InterruptedException e) {
                        e.printStackTrace();
                      }
                    })
                .start();
          }

          latch.countDown();
        });

    binding.stackOverflow.setOnClickListener(view -> stackOverflow());

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
          new Thread(
                  new Runnable() {
                    @Override
                    public void run() {
                      synchronized (mutex) {
                        while (true) {
                          try {
                            Thread.sleep(10000);
                          } catch (InterruptedException e) {
                            e.printStackTrace();
                          }
                        }
                      }
                    }
                  })
              .start();

          new Handler()
              .postDelayed(
                  new Runnable() {
                    @Override
                    public void run() {
                      synchronized (mutex) {
                        // Shouldn't happen
                        throw new IllegalStateException();
                      }
                    }
                  },
                  1000);
        });

    binding.nativeAnr.setOnClickListener(
        view -> {
          new Thread(
                  new Runnable() {
                    @Override
                    public void run() {
                      NativeSample.freezeMysteriously(mutex);
                    }
                  })
              .start();

          new Handler()
              .postDelayed(
                  new Runnable() {
                    @Override
                    public void run() {
                      synchronized (mutex) {
                        // Shouldn't happen
                        throw new IllegalStateException();
                      }
                    }
                  },
                  1000);
        });

    binding.openSecondActivity.setOnClickListener(
        view -> {
          // finishing so its completely destroyed
          finish();
          startActivity(new Intent(this, SecondActivity.class));
        });

    binding.openSampleFragment.setOnClickListener(
        view -> SampleFragment.newInstance().show(getSupportFragmentManager(), null));

    binding.openThirdFragment.setOnClickListener(
        view -> startActivity(new Intent(this, ThirdActivityFragment.class)));

    binding.openGesturesActivity.setOnClickListener(
        view -> startActivity(new Intent(this, GesturesActivity.class)));

    binding.testTimberIntegration.setOnClickListener(
        view -> {
          crashCount++;
          Timber.i("Some info here");
          Timber.e(
              new RuntimeException("Uncaught Exception from Java."),
              "Something wrong happened %d times",
              crashCount);
        });

    binding.openPermissionsActivity.setOnClickListener(
        view -> {
          startActivity(new Intent(this, PermissionsActivity.class));
        });

    binding.openComposeActivity.setOnClickListener(
        view -> {
          startActivity(new Intent(this, ComposeActivity.class));
        });

    binding.openProfilingActivity.setOnClickListener(
        view -> {
          startActivity(new Intent(this, ProfilingActivity.class));
        });

    binding.openFrameDataForSpans.setOnClickListener(
        view -> startActivity(new Intent(this, FrameDataForSpansActivity.class)));

    binding.throwInCoroutine.setOnClickListener(
        view -> {
          CoroutinesUtil.INSTANCE.throwInCoroutine();
        });

    binding.showDialog.setOnClickListener(
        view -> {
          new AlertDialog.Builder(MainActivity.this)
              .setTitle("Example Title")
              .setMessage("Example Message")
              .setPositiveButton(
                  "Close",
                  (dialog, which) -> {
                    if (SharedState.INSTANCE.isOrientationChange()) {
                      int currentOrientation = getResources().getConfiguration().orientation;
                      if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                      } else if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                      }
                    } else {
                      dialog.dismiss();
                    }
                  })
              .show();
        });

    binding.enableReplayDebugMode.setOnClickListener(
        view -> {
          Sentry.replay().enableDebugMaskingOverlay();
        });

    binding.openCameraActivity.setOnClickListener(
        view -> {
          startActivity(new Intent(this, CameraXActivity.class));
        });

    setContentView(binding.getRoot());
  }

  private void stackOverflow() {
    stackOverflow();
  }

  @Override
  protected void onResume() {
    super.onResume();
    screenLoadCount++;
    final ISpan span = Sentry.getSpan();
    if (span != null) {
      ISpan measurementSpan = span.startChild("screen_load_measurement", "test measurement");
      measurementSpan.setMeasurement(
          "screen_load_count", screenLoadCount, new MeasurementUnit.Custom("test"));
      measurementSpan.finish();
    }
    Sentry.reportFullyDisplayed();
  }
}
