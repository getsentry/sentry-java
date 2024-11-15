package io.sentry.samples.android;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import io.sentry.Attachment;
import io.sentry.ISpan;
import io.sentry.MeasurementUnit;
import io.sentry.Sentry;
import io.sentry.UserFeedback;
import io.sentry.instrumentation.file.SentryFileOutputStream;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.samples.android.compose.ComposeActivity;
import io.sentry.samples.android.databinding.ActivityMainBinding;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
          final String message = "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***\nVersion '2020.3.48f1 (b805b124c6b7)', Build type 'Release', Scripting Backend 'il2cpp', CPU 'armeabi-v7a'\nBuild fingerprint: 'xiaomi/cactus/cactus:9/PPR1.180610.011/V11.0.9.0.PCBMIXM:user/release-keys'\nRevision: '0'\nABI: 'arm'\nTimestamp: 2024-11-14 14:25:05+0530\npid: 23910, tid: 24255, name: UnityMain  \u003E\u003E\u003E com.winzo.gold:gameProcess \u003C\u003C\u003C\nuid: 10151\nsignal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x2a\nCause: null pointer dereference\n    r0  00000000  r1  7dd96aa8  r2  83c472b8  r3  83c473a0\n    r4  83c473a0  r5  00000000  r6  00000000  r7  83c472b8\n    r8  00000000  r9  000000ff  r10 000000a0  r11 00001180\n    ip  ffffffec  sp  83c47268  lr  6dcb7953  pc  7ddd3fd4\n\nbacktrace:\n      #00 pc 0098dfd4  /data/app/com.winzo.gold-QfypLwbH1ldLqZgaAor-Uw==/lib/arm/libil2cpp.so (BuildId: 008f8b67eb23e524e5a80af60ee8d260c6bcbbd9)\n";
          final Error rootCause = new Error(message);
          final StackTraceElement[] stackTraceElements = new StackTraceElement[1];
          stackTraceElements[0] = new StackTraceElement("libunity", "0x98dfd4", null, -1);
          rootCause.setStackTrace(stackTraceElements);

          final String errorMessage = "FATAL EXCEPTION [UnityMain]\nUnity version     : 2020.3.48f1\nDevice model      : Xiaomi Redmi 6A\nDevice fingerprint: xiaomi/cactus/cactus:9/PPR1.180610.011/V11.0.9.0.PCBMIXM:user/release-keys\nBuild Type        : Release\nScripting Backend : IL2CPP\nABI               : armeabi-v7a\nStrip Engine Code : true\n";
          final Error error = new Error(errorMessage, rootCause);
          error.setStackTrace(new StackTraceElement[]{});
          throw error;
          //throw new Error("Uncaught Exception from Java.");
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
          String fileName = Calendar.getInstance().getTimeInMillis() + "_file.txt";
          File file = getApplication().getFileStreamPath(fileName);
          try (final FileOutputStream fileOutputStream = new SentryFileOutputStream(file);
              final OutputStreamWriter outputStreamWriter =
                  new OutputStreamWriter(fileOutputStream);
              final Writer writer = new BufferedWriter(outputStreamWriter)) {
            for (int i = 0; i < 1024; i++) {
              // To keep the sample code simple this happens on the main thread. Don't do this in a
              // real app.
              writer.write(String.format(Locale.getDefault(), "%d\n", i));
            }
            writer.flush();
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

    binding.openMetrics.setOnClickListener(
        view -> startActivity(new Intent(this, MetricsActivity.class)));

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
