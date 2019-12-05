// https://github.com/SalomonBrys/ANR-WatchDog/blob/1969075f75f5980e9000eaffbaa13b0daf282dcb/anr-watchdog/src/main/java/com/github/anrwatchdog/ANRWatchDog.java
// Based on the class above. The API unnecessary here was removed.
package io.sentry.android.core;

import android.os.Debug;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/** A watchdog timer thread that detects when the UI thread has frozen. */
@SuppressWarnings("UnusedReturnValue")
final class ANRWatchDog extends Thread {

  private final boolean reportInDebug;
  private final ANRListener anrListener;
  private final IHandler uiHandler;
  private final long timeoutIntervalMills;
  private final ILogger logger;
  private AtomicLong tick = new AtomicLong(0);
  private volatile boolean reported = false;
  private final Runnable ticker =
      () -> {
        tick = new AtomicLong(0);
        reported = false;
      };

  ANRWatchDog(
      long timeoutIntervalMills,
      boolean reportInDebug,
      @NotNull ANRListener listener,
      @NotNull ILogger logger) {
    this(timeoutIntervalMills, reportInDebug, listener, logger, new MainLooperHandler());
  }

  @TestOnly
  ANRWatchDog(
      long timeoutIntervalMills,
      boolean reportInDebug,
      @NotNull ANRListener listener,
      @NotNull ILogger logger,
      @NotNull IHandler uiHandler) {
    super();
    this.reportInDebug = reportInDebug;
    this.anrListener = listener;
    this.timeoutIntervalMills = timeoutIntervalMills;
    this.logger = logger;
    this.uiHandler = uiHandler;
  }

  @SuppressWarnings("NonAtomicOperationOnVolatileField")
  @Override
  public void run() {
    setName("|ANR-WatchDog|");

    long interval = timeoutIntervalMills;
    while (!isInterrupted()) {
      boolean needPost = tick.get() == 0;
      tick.addAndGet(interval);
      if (needPost) {
        uiHandler.post(ticker);
      }

      try {
        Thread.sleep(interval);
      } catch (InterruptedException e) {
        logger.log(SentryLevel.WARNING, "Interrupted: %s", e.getMessage());
        return;
      }

      // If the main thread has not handled ticker, it is blocked. ANR.
      if (tick.get() != 0 && !reported) {
        //noinspection ConstantConditions
        if (!reportInDebug && (Debug.isDebuggerConnected() || Debug.waitingForDebugger())) {
          logger.log(
              SentryLevel.DEBUG,
              "An ANR was detected but ignored because the debugger is connected.");
          reported = true;
          continue;
        }

        logger.log(SentryLevel.INFO, "Raising ANR");
        final String message =
            "Application Not Responding for at least " + timeoutIntervalMills + " ms.";

        final ApplicationNotResponding error =
            new ApplicationNotResponding(message, uiHandler.getThread());
        anrListener.onAppNotResponding(error);
        interval = timeoutIntervalMills;
        reported = true;
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
