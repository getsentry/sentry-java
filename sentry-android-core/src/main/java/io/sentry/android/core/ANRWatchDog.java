// https://github.com/SalomonBrys/ANR-WatchDog/blob/1969075f75f5980e9000eaffbaa13b0daf282dcb/anr-watchdog/src/main/java/com/github/anrwatchdog/ANRWatchDog.java
// Based on the class above. The API unnecessary here was removed.
package io.sentry.android.core;

import static android.app.ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/** A watchdog timer thread that detects when the UI thread has frozen. */
@SuppressWarnings("UnusedReturnValue")
final class ANRWatchDog extends Thread {

  private final boolean reportInDebug;
  private final ANRListener anrListener;
  private final IHandler uiHandler;
  private final long timeoutIntervalMillis;
  private final @NotNull ILogger logger;
  private AtomicLong tick = new AtomicLong(0);
  private AtomicBoolean reported = new AtomicBoolean(false);

  private final @NotNull Context context;

  @SuppressWarnings("UnnecessaryLambda")
  private final Runnable ticker =
      () -> {
        tick = new AtomicLong(0);
        reported.set(false);
      };

  ANRWatchDog(
      long timeoutIntervalMillis,
      boolean reportInDebug,
      @NotNull ANRListener listener,
      @NotNull ILogger logger,
      final @NotNull Context context) {
    this(timeoutIntervalMillis, reportInDebug, listener, logger, new MainLooperHandler(), context);
  }

  @TestOnly
  ANRWatchDog(
      long timeoutIntervalMillis,
      boolean reportInDebug,
      @NotNull ANRListener listener,
      @NotNull ILogger logger,
      @NotNull IHandler uiHandler,
      final @NotNull Context context) {
    super();
    this.reportInDebug = reportInDebug;
    this.anrListener = listener;
    this.timeoutIntervalMillis = timeoutIntervalMillis;
    this.logger = logger;
    this.uiHandler = uiHandler;
    this.context = context;
  }

  @Override
  public void run() {
    setName("|ANR-WatchDog|");

    long interval = timeoutIntervalMillis;
    while (!isInterrupted()) {
      boolean needPost = tick.get() == 0;
      tick.addAndGet(interval);
      if (needPost) {
        uiHandler.post(ticker);
      }

      try {
        Thread.sleep(interval);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.log(SentryLevel.WARNING, "Interrupted: %s", e.getMessage());
        return;
      }

      // If the main thread has not handled ticker, it is blocked. ANR.
      if (tick.get() != 0 && !reported.get()) {
        if (!reportInDebug && (Debug.isDebuggerConnected() || Debug.waitingForDebugger())) {
          logger.log(
              SentryLevel.DEBUG,
              "An ANR was detected but ignored because the debugger is connected.");
          reported.set(true);
          continue;
        }

        // we only raise an ANR event if the process is in ANR state.
        // if ActivityManager is not available, we'll still be able to send ANRs
        final ActivityManager am =
            (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        if (am != null) {
          final List<ActivityManager.ProcessErrorStateInfo> processesInErrorState =
              am.getProcessesInErrorState();
          // if list is null, there's no process in ANR state.
          if (processesInErrorState == null) {
            continue;
          }
          boolean isAnr = false;
          for (ActivityManager.ProcessErrorStateInfo item : processesInErrorState) {
            if (item.condition == NOT_RESPONDING) {
              isAnr = true;
              break;
            }
          }
          if (!isAnr) {
            continue;
          }
        }

        logger.log(SentryLevel.INFO, "Raising ANR");
        final String message =
            "Application Not Responding for at least " + timeoutIntervalMillis + " ms.";

        final ApplicationNotResponding error =
            new ApplicationNotResponding(message, uiHandler.getThread());
        anrListener.onAppNotResponding(error);
        interval = timeoutIntervalMillis;

        reported.set(true);
      }
    }
  }

  public interface ANRListener {
    /**
     * Called when an ANR is detected.
     *
     * @param error The error describing the ANR.
     */
    void onAppNotResponding(ApplicationNotResponding error);
  }
}
