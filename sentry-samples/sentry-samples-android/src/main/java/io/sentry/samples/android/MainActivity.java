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
          final String message = "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***\nVersion '2020.3.48f1 (b805b124c6b7)', Build type 'Release', Scripting Backend 'il2cpp', CPU 'arm64-v8a'\nBuild fingerprint: 'Itel/S665L-GL/itel-S665L:12/SP1A.210812.016/GL-20240131V499:user/release-keys'\nRevision: '0'\nABI: 'arm64'\nTimestamp: 2024-11-15 20:58:37+0530\npid: 20456, tid: 20456, name: old:gameProcess  \u003E\u003E\u003E com.winzo.gold:gameProcess \u003C\u003C\u003C\nuid: 10340\nsignal 6 (SIGABRT), code -1 (SI_QUEUE), fault addr --------\n    x0  0000000000000000  x1  0000000000004fe8  x2  0000000000000006  x3  0000007ff6fa2220\n    x4  0000000080808080  x5  0000000080808080  x6  0000000080808080  x7  8080808080808080\n    x8  00000000000000f0  x9  09cc825c0834da08  x10 0000000000000000  x11 ffffff80fffffb9f\n    x12 0000000000000001  x13 00000acd54ab6d2f  x14 001522462cf289f6  x15 0000000000000028\n    x16 0000007c32a9f050  x17 0000007c32a7cb00  x18 00000077ec97f7b0  x19 0000000000004fe8\n    x20 0000000000004fe8  x21 00000000ffffffff  x22 0000007955b0d26d  x23 0000007955b0d035\n    x24 0000000000000007  x25 000000007042b8b8  x26 0000007ff6fa254c  x27 0000007ff6fa2540\n    x28 0000007ff6fa2560  x29 0000007ff6fa22a0\n    sp  0000007ff6fa2200  lr  0000007c32a2f7a4  pc  0000007c32a2f7d0\n\nbacktrace:\n      #00 pc 000000000004f7d0  /apex/com.android.runtime/lib64/bionic/libc.so (abort+164) (BuildId: 5d21548447ff2f9aab8359665aaabf4f)\n      #01 pc 0000000000051574  /apex/com.android.runtime/lib64/bionic/libc.so (fdopendir) (BuildId: 5d21548447ff2f9aab8359665aaabf4f)\n      #02 pc 00000000000b2394  /apex/com.android.runtime/lib64/bionic/libc.so (NonPI::MutexLockWithTimeout(pthread_mutex_internal_t*, bool, timespec const*)) (BuildId: 5d21548447ff2f9aab8359665aaabf4f)\n      #03 pc 00000000000b2224  /apex/com.android.runtime/lib64/bionic/libc.so (pthread_mutex_lock+192) (BuildId: 5d21548447ff2f9aab8359665aaabf4f)\n      #04 pc 0000000000095664  /system/lib64/libc++.so (std::__1::mutex::lock()+8) (BuildId: 1f54b1cc0b8cf4ebefd07b7c5acde867)\n      #05 pc 000000000000df14  /system/lib64/libsoundpool.so (android::soundpool::Stream::pause(int)+80) (BuildId: ccd1a6dd5b2e85420412399940942612)\n      #06 pc 00000000003603c0  /data/misc/apexdata/com.android.art/dalvik-cache/arm64/boot.oat\n";
          final Error rootCause = new Error(message);
          final StackTraceElement[] stackTraceElements = new StackTraceElement[1];
          stackTraceElements[0] = new StackTraceElement("libunity", "0x98dfd4", null, -1);
          rootCause.setStackTrace(stackTraceElements);

          final String errorMessage = "FATAL EXCEPTION [UnityMain]\nUnity version     : 2020.3.48f1\nDevice model      : ITEL itel S665L\nDevice fingerprint: Itel/S665L-GL/itel-S665L:12/SP1A.210812.016/GL-20240131V499:user/release-keys\nBuild Type        : Release\nScripting Backend : IL2CPP\nABI               : arm64-v8a\nStrip Engine Code : true\n";
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
