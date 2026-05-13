package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Debug;
import io.sentry.AnrHeartbeatRegistry;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ILogger;
import io.sentry.Integration;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.DebugImage;
import io.sentry.protocol.DebugMeta;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.SentryThread;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Heartbeat-based app-hang detection for runtimes whose main thread is not an Android Looper
 * thread (e.g. Unity, Unreal). The host runtime calls {@link io.sentry.Sentry#notifyAnrThreadAlive()}
 * regularly from the monitored thread; if no heartbeat arrives within {@link
 * SentryAndroidOptions#getAnrTimeoutIntervalMillis()}, an ANR event is reported with the captured
 * native stack of the monitored thread (when the NDK companion is available).
 *
 * <p>Self-gates on {@code SentryAndroidOptions.anrThreadId == 0} at register time, so installing
 * this integration unconditionally is safe.
 *
 * <p>Orthogonal to {@link AnrIntegration} (Looper probe) and {@link AnrV2Integration}
 * ({@code ApplicationExitInfo}): all three can coexist because they monitor different signals.
 */
public final class AnrHeartbeatIntegration implements Integration, Closeable {

  /** Polling cadence of the watchdog thread, in ms. Independent of the heartbeat cadence. */
  static final long POLLING_INTERVAL_MS = 500L;

  private final @NotNull Context context;

  @SuppressLint("StaticFieldLeak")
  @Nullable
  private static volatile HeartbeatWatchDog watchdog;

  private static final @NotNull AutoClosableReentrantLock watchdogLock =
      new AutoClosableReentrantLock();

  @Nullable private SentryOptions options;

  public AnrHeartbeatIntegration(final @NotNull Context context) {
    this.context = ContextUtils.getApplicationContext(context);
  }

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "SentryOptions is required");
    final SentryAndroidOptions androidOptions = (SentryAndroidOptions) options;
    final ILogger logger = androidOptions.getLogger();

    final long anrThreadId = androidOptions.getAnrThreadId();
    if (anrThreadId == 0) {
      logger.log(
          SentryLevel.DEBUG,
          "AnrHeartbeatIntegration disabled: SentryAndroidOptions.anrThreadId is not set.");
      return;
    }

    if (!androidOptions.isAnrEnabled()) {
      logger.log(SentryLevel.DEBUG, "AnrHeartbeatIntegration disabled: ANR detection is off.");
      return;
    }

    try (final @NotNull ISentryLifecycleToken ignored = watchdogLock.acquire()) {
      if (watchdog != null) {
        logger.log(SentryLevel.DEBUG, "AnrHeartbeatIntegration already installed; skipping.");
        return;
      }

      // Resolve the monitored thread name once at register time. Falls back to a generic name
      // if /proc is unreadable, which is rare on real devices.
      final @Nullable String threadName = readThreadName(anrThreadId);

      final HeartbeatWatchDog wd =
          new HeartbeatWatchDog(
              androidOptions.getAnrTimeoutIntervalMillis(),
              POLLING_INTERVAL_MS,
              androidOptions.isAnrReportInDebug(),
              error -> reportAnr(scopes, androidOptions, anrThreadId, threadName, error),
              logger);
      wd.start();
      watchdog = wd;
      AnrHeartbeatRegistry.setListener(wd::notifyAlive);
      addIntegrationToSdkVersion("AnrHeartbeat");

      logger.log(
          SentryLevel.DEBUG,
          "AnrHeartbeatIntegration installed (tid=%d, timeout=%d ms, thread=%s).",
          anrThreadId,
          androidOptions.getAnrTimeoutIntervalMillis(),
          threadName);
    }
  }

  @Override
  public void close() {
    try (final @NotNull ISentryLifecycleToken ignored = watchdogLock.acquire()) {
      AnrHeartbeatRegistry.setListener(null);
      if (watchdog != null) {
        watchdog.interrupt();
        watchdog = null;
        if (options != null) {
          options.getLogger().log(SentryLevel.DEBUG, "AnrHeartbeatIntegration removed.");
        }
      }
    }
  }

  @TestOnly
  @Nullable
  HeartbeatWatchDog getWatchdog() {
    return watchdog;
  }

  private void reportAnr(
      final @NotNull IScopes scopes,
      final @NotNull SentryAndroidOptions options,
      final long tid,
      final @Nullable String threadName,
      final @NotNull ApplicationNotResponding error) {
    options.getLogger().log(SentryLevel.INFO, "ANR triggered with message: %s", error.getMessage());

    final boolean isAppInBackground =
        Boolean.TRUE.equals(AppState.getInstance().isInBackground());

    String message = "ANR for at least " + options.getAnrTimeoutIntervalMillis() + " ms.";
    if (isAppInBackground) {
      message = "Background " + message;
    }
    final ApplicationNotResponding wrapped = new ApplicationNotResponding(message);
    final Mechanism mechanism = new Mechanism();
    mechanism.setType("ANR");
    // The watchdog thread is not the culprit — let event processors prefer the monitored thread.
    final Throwable throwable = new ExceptionMechanismException(mechanism, wrapped, null, true);

    final SentryEvent event = new SentryEvent(throwable);
    event.setLevel(SentryLevel.ERROR);

    attachNativeStack(event, tid, threadName, options);

    final AnrIntegration.AnrHint anrHint = new AnrIntegration.AnrHint(isAppInBackground);
    final Hint hint = HintUtils.createWithTypeCheckHint(anrHint);
    scopes.captureEvent(event, hint);
  }

  private void attachNativeStack(
      final @NotNull SentryEvent event,
      final long tid,
      final @Nullable String threadName,
      final @NotNull SentryAndroidOptions options) {
    try {
      final long[] addresses = io.sentry.ndk.SentryNdk.captureThreadStack(tid);
      if (addresses.length == 0) {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Captured 0 native frames for thread %d; skipping native stack attachment.",
                tid);
        return;
      }

      final List<SentryStackFrame> frames = new ArrayList<>(addresses.length);
      final Set<String> addressSet = new HashSet<>(addresses.length);
      // Sentry stack frames are ordered with the oldest caller first; native unwinders typically
      // return frames with the most recent at index 0. Reverse on attach.
      for (int i = addresses.length - 1; i >= 0; i--) {
        final String addr = "0x" + Long.toHexString(addresses[i]);
        final SentryStackFrame frame = new SentryStackFrame();
        frame.setInstructionAddr(addr);
        // Mark each frame as native so the symbolicator resolves against the attached debug
        // images (parent event platform stays "java").
        frame.setPlatform("native");
        frames.add(frame);
        addressSet.add(addr);
      }

      final IDebugImagesLoader debugImagesLoader = options.getDebugImagesLoader();
      final Set<DebugImage> images = debugImagesLoader.loadDebugImagesForAddresses(addressSet);
      if (images != null && !images.isEmpty()) {
        DebugMeta debugMeta = event.getDebugMeta();
        if (debugMeta == null) {
          debugMeta = new DebugMeta();
          event.setDebugMeta(debugMeta);
        }
        debugMeta.setImages(new ArrayList<>(images));
      } else {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "No debug images matched the %d captured native frame addresses for ANR thread %d; "
                    + "frames will not symbolicate.",
                addressSet.size(),
                tid);
      }

      final SentryStackTrace stacktrace = new SentryStackTrace(frames);

      final SentryThread thread = new SentryThread();
      thread.setName(threadName != null ? threadName : "anr-thread");
      thread.setId(tid);
      thread.setCrashed(true);
      thread.setStacktrace(stacktrace);

      final List<SentryThread> threads = new ArrayList<>();
      threads.add(thread);
      event.setThreads(threads);
    } catch (Throwable t) {
      // NoClassDefFoundError when sentry-native-ndk isn't on the runtime classpath, anything
      // else if the unwinder itself fails — either way the ANR event still goes out, just
      // without the native stack.
      options
          .getLogger()
          .log(SentryLevel.ERROR, t, "Failed to capture native stack for thread %d", tid);
    }
  }

  private static @Nullable String readThreadName(final long tid) {
    final File commFile = new File("/proc/self/task/" + tid + "/comm");
    if (!commFile.exists()) {
      return null;
    }
    try (final BufferedReader reader = new BufferedReader(new FileReader(commFile))) {
      final String line = reader.readLine();
      return line != null ? line.trim() : null;
    } catch (Throwable t) {
      return null;
    }
  }

  /**
   * Watchdog thread. Polls a heartbeat timestamp updated via {@link #notifyAlive()} and fires the
   * listener when the monitored thread hasn't reported liveness within the configured timeout.
   * Suppresses detection while the app is backgrounded (AppState-aware), keeping the timestamp
   * fresh so the next foreground entry gets a full timeout window.
   */
  static final class HeartbeatWatchDog extends Thread {
    private final long timeoutMs;
    private final long pollingIntervalMs;
    private final boolean reportInDebug;
    private final @NotNull ANRWatchDog.ANRListener listener;
    private final @NotNull ILogger logger;
    private volatile long lastHeartbeatNs;
    private final AtomicBoolean reported = new AtomicBoolean(false);

    HeartbeatWatchDog(
        final long timeoutMs,
        final long pollingIntervalMs,
        final boolean reportInDebug,
        final @NotNull ANRWatchDog.ANRListener listener,
        final @NotNull ILogger logger) {
      super("|ANR-Heartbeat-WatchDog|");
      this.timeoutMs = timeoutMs;
      this.pollingIntervalMs = pollingIntervalMs;
      this.reportInDebug = reportInDebug;
      this.listener = listener;
      this.logger = logger;
      this.lastHeartbeatNs = System.nanoTime();
    }

    void notifyAlive() {
      lastHeartbeatNs = System.nanoTime();
      reported.set(false);
    }

    @Override
    public void run() {
      while (!isInterrupted()) {
        try {
          Thread.sleep(pollingIntervalMs);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }

        // While backgrounded the host runtime may not be driven (e.g. Unity's main thread is
        // paused when the app has no surface). Skip detection and keep the timestamp fresh so
        // the next foreground entry has a full timeout window.
        if (Boolean.TRUE.equals(AppState.getInstance().isInBackground())) {
          lastHeartbeatNs = System.nanoTime();
          continue;
        }

        final long elapsedMs = (System.nanoTime() - lastHeartbeatNs) / 1_000_000L;
        if (elapsedMs <= timeoutMs) {
          continue;
        }

        if (!reportInDebug && (Debug.isDebuggerConnected() || Debug.waitingForDebugger())) {
          logger.log(
              SentryLevel.DEBUG,
              "ANR heartbeat timeout ignored because the debugger is connected.");
          reported.set(true);
          continue;
        }

        if (reported.compareAndSet(false, true)) {
          final ApplicationNotResponding error =
              new ApplicationNotResponding(
                  "Application Not Responding for at least " + timeoutMs + " ms.");
          listener.onAppNotResponding(error);
        }
      }
    }
  }
}
