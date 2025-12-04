package io.sentry.android.core.anr;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.Integration;
import io.sentry.NoOpLogger;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.AppState;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class AnrProfilingIntegration
    implements Integration, Closeable, AppState.AppStateListener, Runnable {

  public static final long POLLING_INTERVAL_MS = 66;
  private static final long THRESHOLD_SUSPICION_MS = 1000;
  public static final long THRESHOLD_ANR_MS = 4000;

  private final AtomicBoolean enabled = new AtomicBoolean(true);
  private final Runnable updater = () -> lastMainThreadExecutionTime = SystemClock.uptimeMillis();
  private final @NotNull AutoClosableReentrantLock lifecycleLock = new AutoClosableReentrantLock();
  private final @NotNull AutoClosableReentrantLock profileManagerLock =
      new AutoClosableReentrantLock();

  private volatile long lastMainThreadExecutionTime = SystemClock.uptimeMillis();
  private volatile MainThreadState mainThreadState = MainThreadState.IDLE;
  private volatile @Nullable AnrProfileManager profileManager;
  private volatile @NotNull ILogger logger = NoOpLogger.getInstance();
  private volatile @Nullable SentryOptions options;
  private volatile @Nullable Thread thread = null;
  private volatile boolean inForeground = false;

  @Override
  public void register(@NotNull IScopes scopes, @NotNull SentryOptions options) {
    this.options = options;
    logger = options.getLogger();
    AppState.getInstance().addAppStateListener(this);
  }

  @Override
  public void close() throws IOException {
    onBackground();
    enabled.set(false);
    AppState.getInstance().removeAppStateListener(this);

    try (final @NotNull ISentryLifecycleToken ignored = profileManagerLock.acquire()) {
      final @Nullable AnrProfileManager p = profileManager;
      if (p != null) {
        p.close();
      }
    }
  }

  @Override
  public void onForeground() {
    if (!enabled.get()) {
      return;
    }
    try (final @NotNull ISentryLifecycleToken ignored = lifecycleLock.acquire()) {
      if (inForeground) {
        return;
      }
      inForeground = true;

      final @Nullable Thread oldThread = thread;
      if (oldThread != null) {
        oldThread.interrupt();
      }

      final @NotNull Thread newThread = new Thread(this, "AnrProfilingIntegration");
      newThread.start();
      thread = newThread;
    }
  }

  @Override
  public void onBackground() {
    if (!enabled.get()) {
      return;
    }
    try (final @NotNull ISentryLifecycleToken ignored = lifecycleLock.acquire()) {
      if (!inForeground) {
        return;
      }

      inForeground = false;
      final @Nullable Thread oldThread = thread;
      if (oldThread != null) {
        oldThread.interrupt();
      }
    }
  }

  @Override
  public void run() {
    // get main thread Handler so we can post messages
    final Looper mainLooper = Looper.getMainLooper();
    final Thread mainThread = mainLooper.getThread();
    final Handler mainHandler = new Handler(mainLooper);

    try {
      while (enabled.get() && !Thread.currentThread().isInterrupted()) {
        try {
          checkMainThread(mainThread);

          mainHandler.removeCallbacks(updater);
          mainHandler.post(updater);

          // noinspection BusyWait
          Thread.sleep(POLLING_INTERVAL_MS);
        } catch (InterruptedException e) {
          // Restore interrupt status and exit the polling loop
          Thread.currentThread().interrupt();
          return;
        }
      }
    } catch (Throwable t) {
      logger.log(SentryLevel.WARNING, "Failed execute AnrStacktraceIntegration", t);
    }
  }

  @ApiStatus.Internal
  protected void checkMainThread(final @NotNull Thread mainThread) throws IOException {
    final long now = SystemClock.uptimeMillis();
    final long diff = now - lastMainThreadExecutionTime;

    if (diff < 1000) {
      mainThreadState = MainThreadState.IDLE;
    }

    if (mainThreadState == MainThreadState.IDLE && diff > THRESHOLD_SUSPICION_MS) {
      logger.log(SentryLevel.DEBUG, "ANR: main thread is suspicious");
      mainThreadState = MainThreadState.SUSPICIOUS;
      clearStacks();
    }

    // if we are suspicious, we need to collect stack traces
    if (mainThreadState == MainThreadState.SUSPICIOUS
        || mainThreadState == MainThreadState.ANR_DETECTED) {
      final long start = SystemClock.uptimeMillis();
      final @NotNull AnrStackTrace trace =
          new AnrStackTrace(System.currentTimeMillis(), mainThread.getStackTrace());
      final long duration = SystemClock.uptimeMillis() - start;
      logger.log(
          SentryLevel.DEBUG,
          "AnrWatchdog: capturing main thread stacktrace took " + duration + "ms");

      addStackTrace(trace);
    }

    // TODO is this still required,
    // maybe add stop condition
    if (mainThreadState == MainThreadState.SUSPICIOUS && diff > THRESHOLD_ANR_MS) {
      logger.log(SentryLevel.DEBUG, "ANR: main thread ANR threshold reached");
      mainThreadState = MainThreadState.ANR_DETECTED;
    }
  }

  @TestOnly
  @NotNull
  protected MainThreadState getState() {
    return mainThreadState;
  }

  @TestOnly
  @NonNull
  protected AnrProfileManager getProfileManager() {
    try (final @NotNull ISentryLifecycleToken ignored = profileManagerLock.acquire()) {
      if (profileManager == null) {
        final @NotNull SentryOptions opts =
            Objects.requireNonNull(options, "Options can't be null");
        final @NotNull File currentFile =
            AnrProfileRotationHelper.getCurrentFile(new File(opts.getCacheDirPath()));
        profileManager = new AnrProfileManager(opts, currentFile);
      }

      return profileManager;
    }
  }

  private void clearStacks() throws IOException {
    getProfileManager().clear();
  }

  private void addStackTrace(@NotNull final AnrStackTrace trace) throws IOException {
    getProfileManager().add(trace);
  }

  protected enum MainThreadState {
    IDLE,
    SUSPICIOUS,
    ANR_DETECTED,
  }
}
