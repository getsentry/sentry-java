// https://github.com/SalomonBrys/ANR-WatchDog/blob/1969075f75f5980e9000eaffbaa13b0daf282dcb/anr-watchdog/src/main/java/com/github/anrwatchdog/ANRWatchDog.java
// Based on the class above. The API unnecessary here was removed.
package io.sentry.android.core;

import static android.app.ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.SystemClock;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/** A watchdog timer thread that detects when the UI thread has frozen. */
@SuppressWarnings("UnusedReturnValue")
final class ANRWatchDog extends Thread {

  private final boolean reportInDebug;
  private final ANRListener anrListener;
  private final MainLooperHandler uiHandler;
  private final TimeProvider timeProvider;
  /** the interval in which we check if there's an ANR, in ms */
  private long pollingIntervalMs;

  private final long timeoutIntervalMillis;
  private final @NotNull ILogger logger;

  private volatile long lastKnownActiveUiTimestampMs = 0;
  private final AtomicBoolean reported = new AtomicBoolean(false);

  private final @NotNull Context context;

  @SuppressWarnings("UnnecessaryLambda")
  private final Runnable ticker;

  ANRWatchDog(
      long timeoutIntervalMillis,
      boolean reportInDebug,
      @NotNull ANRListener listener,
      @NotNull ILogger logger,
      final @NotNull Context context) {
    this(
        () -> SystemClock.uptimeMillis(),
        timeoutIntervalMillis,
        500,
        reportInDebug,
        listener,
        logger,
        new MainLooperHandler(),
        context);
  }

  @TestOnly
  ANRWatchDog(
      @NotNull final TimeProvider timeProvider,
      long timeoutIntervalMillis,
      long pollingIntervalMillis,
      boolean reportInDebug,
      @NotNull ANRListener listener,
      @NotNull ILogger logger,
      @NotNull MainLooperHandler uiHandler,
      final @NotNull Context context) {

    super("|ANR-WatchDog|");

    this.timeProvider = timeProvider;
    this.timeoutIntervalMillis = timeoutIntervalMillis;
    this.pollingIntervalMs = pollingIntervalMillis;
    this.reportInDebug = reportInDebug;
    this.anrListener = listener;
    this.logger = logger;
    this.uiHandler = uiHandler;
    this.context = context;
    this.ticker =
        () -> {
          lastKnownActiveUiTimestampMs = timeProvider.getCurrentTimeMillis();
          reported.set(false);
        };

    if (timeoutIntervalMillis < (pollingIntervalMs * 2)) {
      throw new IllegalArgumentException(
          String.format(
              "ANRWatchDog: timeoutIntervalMillis has to be at least %d ms",
              pollingIntervalMs * 2));
    }
  }

  @Override
  public void run() {
    // right when the watchdog gets started, let's assume there's no ANR
    ticker.run();

    while (!isInterrupted()) {
      uiHandler.post(ticker);

      try {
        Thread.sleep(pollingIntervalMs);
      } catch (InterruptedException e) {
        try {
          Thread.currentThread().interrupt();
        } catch (SecurityException ignored) {
          logger.log(
              SentryLevel.WARNING,
              "Failed to interrupt due to SecurityException: %s",
              e.getMessage());
          return;
        }
        logger.log(SentryLevel.WARNING, "Interrupted: %s", e.getMessage());
        return;
      }

      final long unresponsiveDurationMs =
          timeProvider.getCurrentTimeMillis() - lastKnownActiveUiTimestampMs;

      // If the main thread has not handled ticker, it is blocked. ANR.
      if (unresponsiveDurationMs > timeoutIntervalMillis) {
        if (!reportInDebug && (Debug.isDebuggerConnected() || Debug.waitingForDebugger())) {
          logger.log(
              SentryLevel.DEBUG,
              "An ANR was detected but ignored because the debugger is connected.");
          reported.set(true);
          continue;
        }

        if (isProcessNotResponding() && reported.compareAndSet(false, true)) {
          final String message =
              "Application Not Responding for at least " + timeoutIntervalMillis + " ms.";

          final ApplicationNotResponding error =
              new ApplicationNotResponding(message, uiHandler.getThread());
          anrListener.onAppNotResponding(error);
        }
      }
    }
  }

  private boolean isProcessNotResponding() {
    // we only raise an ANR event if the process is in ANR state.
    // if ActivityManager is not available, we'll still be able to send ANRs
    final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

    if (am != null) {
      List<ActivityManager.ProcessErrorStateInfo> processesInErrorState = null;
      try {
        // It can throw RuntimeException or OutOfMemoryError
        processesInErrorState = am.getProcessesInErrorState();
      } catch (Throwable e) {
        logger.log(SentryLevel.ERROR, "Error getting ActivityManager#getProcessesInErrorState.", e);
      }
      // if list is null, there's no process in ANR state.
      if (processesInErrorState != null) {
        for (ActivityManager.ProcessErrorStateInfo item : processesInErrorState) {
          if (item.condition == NOT_RESPONDING) {
            return true;
          }
        }
      }
      // when list is empty, or there's no element NOT_RESPONDING, we can assume the app is not
      // blocked
      return false;
    }
    return true;
  }

  @FunctionalInterface
  interface TimeProvider {
    long getCurrentTimeMillis();
  }

  public interface ANRListener {
    /**
     * Called when an ANR is detected.
     *
     * @param error The error describing the ANR.
     */
    void onAppNotResponding(@NotNull ApplicationNotResponding error);
  }
}
