package io.sentry.android.core.anr;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.Integration;
import io.sentry.NoOpLogger;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.AppState;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class AnrProfilingIntegration
    implements Integration, Closeable, AppState.AppStateListener, Runnable {

  public static final long POLLING_INTERVAL_MS = 66;
  private static final long THRESHOLD_SUSPICION_MS = 1000;
  public static final long THRESHOLD_ANR_MS = 4000;
  static final int MAX_NUM_STACKS = (int) (10_000 / POLLING_INTERVAL_MS);

  private final AtomicBoolean enabled = new AtomicBoolean(true);
  private final Runnable updater = () -> lastMainThreadExecutionTime = SystemClock.uptimeMillis();
  private final @NotNull AutoClosableReentrantLock lifecycleLock = new AutoClosableReentrantLock();
  private final @NotNull AutoClosableReentrantLock profileManagerLock =
      new AutoClosableReentrantLock();

  private volatile long lastMainThreadExecutionTime = SystemClock.uptimeMillis();
  final AtomicInteger numCollectedStacks = new AtomicInteger();
  private volatile MainThreadState mainThreadState = MainThreadState.IDLE;
  private volatile @Nullable AnrProfileManager profileManager;
  private volatile @NotNull ILogger logger = NoOpLogger.getInstance();
  private volatile @Nullable SentryAndroidOptions options;
  private volatile @Nullable Thread thread = null;
  private volatile boolean inForeground = false;

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");
    this.logger = options.getLogger();

    if (options.getCacheDirPath() == null) {
      logger.log(SentryLevel.WARNING, "ANR Profiling is enabled but cacheDirPath is not set");
      return;
    }

    if (((SentryAndroidOptions) options).isEnableAnrProfiling()) {
      addIntegrationToSdkVersion("AnrProfiling");
      AppState.getInstance().addAppStateListener(this);
    }
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
      profileManager = null;
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
      updater.run();

      final @Nullable Thread oldThread = thread;
      if (oldThread != null) {
        oldThread.interrupt();
      }

      final @NotNull Thread profilingThread = new Thread(this, "AnrProfilingIntegration");
      profilingThread.start();
      thread = profilingThread;
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
      logger.log(SentryLevel.WARNING, "Failed to execute AnrStacktraceIntegration", t);
    }
  }

  @ApiStatus.Internal
  protected void checkMainThread(final @NotNull Thread mainThread) throws IOException {
    final long now = SystemClock.uptimeMillis();
    final long diff = now - lastMainThreadExecutionTime;

    if (diff < THRESHOLD_SUSPICION_MS) {
      mainThreadState = MainThreadState.IDLE;
    }

    if (mainThreadState == MainThreadState.IDLE && diff > THRESHOLD_SUSPICION_MS) {
      if (logger.isEnabled(SentryLevel.DEBUG)) {
        logger.log(SentryLevel.DEBUG, "ANR: main thread is suspicious");
      }
      mainThreadState = MainThreadState.SUSPICIOUS;
      clearStacks();
    }

    // if we are suspicious, we need to collect stack traces
    if (mainThreadState == MainThreadState.SUSPICIOUS
        || mainThreadState == MainThreadState.ANR_DETECTED) {
      if (numCollectedStacks.get() < MAX_NUM_STACKS) {
        final long start = SystemClock.uptimeMillis();
        final @NotNull AnrStackTrace trace =
            new AnrStackTrace(System.currentTimeMillis(), mainThread.getStackTrace());
        final long duration = SystemClock.uptimeMillis() - start;
        if (logger.isEnabled(SentryLevel.DEBUG)) {
          logger.log(
              SentryLevel.DEBUG,
              "AnrWatchdog: capturing main thread stacktrace took " + duration + "ms");
        }
        addStackTrace(trace);
      } else {
        if (logger.isEnabled(SentryLevel.DEBUG)) {
          logger.log(
              SentryLevel.DEBUG,
              "ANR: reached maximum number of collected stack traces, skipping further collection");
        }
      }
    }

    if (mainThreadState == MainThreadState.SUSPICIOUS && diff > THRESHOLD_ANR_MS) {
      if (logger.isEnabled(SentryLevel.DEBUG)) {
        logger.log(SentryLevel.DEBUG, "ANR: main thread ANR threshold reached");
      }
      mainThreadState = MainThreadState.ANR_DETECTED;
    }
  }

  @TestOnly
  @NotNull
  protected MainThreadState getState() {
    return mainThreadState;
  }

  @TestOnly
  @NotNull
  protected AnrProfileManager getProfileManager() {
    try (final @NotNull ISentryLifecycleToken ignored = profileManagerLock.acquire()) {
      if (profileManager == null) {
        final @NotNull SentryOptions opts =
            Objects.requireNonNull(options, "Options can't be null");
        final @Nullable String cacheDirPath = opts.getCacheDirPath();
        if (cacheDirPath == null) {
          throw new IllegalStateException("cacheDirPath is required for ANR profiling");
        }
        final @NotNull File currentFile =
            AnrProfileRotationHelper.getFileForRecording(new File(cacheDirPath));
        profileManager = new AnrProfileManager(opts, currentFile);
      }

      return profileManager;
    }
  }

  private void clearStacks() throws IOException {
    numCollectedStacks.set(0);
    getProfileManager().clear();
  }

  private void addStackTrace(@NotNull final AnrStackTrace trace) throws IOException {
    numCollectedStacks.incrementAndGet();
    getProfileManager().add(trace);
  }

  protected enum MainThreadState {
    IDLE,
    SUSPICIOUS,
    ANR_DETECTED,
  }
}
