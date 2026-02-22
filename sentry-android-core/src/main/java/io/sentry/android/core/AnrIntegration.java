package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.annotation.SuppressLint;
import android.content.Context;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.Integration;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.hints.AbnormalExit;
import io.sentry.hints.TransactionEnd;
import io.sentry.protocol.Mechanism;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * When the UI thread of an Android app is blocked for too long, an "Application Not Responding"
 * (ANR) error is triggered. Sends an event if an ANR happens
 */
public final class AnrIntegration implements Integration, Closeable {

  private final @NotNull Context context;
  private boolean isClosed = false;
  private final @NotNull AutoClosableReentrantLock startLock = new AutoClosableReentrantLock();

  public AnrIntegration(final @NotNull Context context) {
    this.context = ContextUtils.getApplicationContext(context);
  }

  /**
   * Responsible for watching UI thread. being static to avoid multiple instances running at the
   * same time.
   */
  @SuppressLint("StaticFieldLeak") // we have watchDogLock to avoid those leaks
  private static @Nullable ANRWatchDog anrWatchDog;

  private @Nullable SentryOptions options;

  protected static final @NotNull AutoClosableReentrantLock watchDogLock =
      new AutoClosableReentrantLock();

  @Override
  public final void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "SentryOptions is required");
    register(scopes, (SentryAndroidOptions) options);
  }

  private void register(
      final @NotNull IScopes scopes, final @NotNull SentryAndroidOptions options) {
    options
        .getLogger()
        .log(SentryLevel.DEBUG, "AnrIntegration enabled: %s", options.isAnrEnabled());

    if (options.isAnrEnabled()) {
      addIntegrationToSdkVersion("Anr");
      try {
        options
            .getExecutorService()
            .submit(
                () -> {
                  try (final @NotNull ISentryLifecycleToken ignored = startLock.acquire()) {
                    if (!isClosed) {
                      startAnrWatchdog(scopes, options);
                    }
                  }
                });
      } catch (Throwable e) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Failed to start AnrIntegration on executor thread.", e);
      }
    }
  }

  private void startAnrWatchdog(
      final @NotNull IScopes scopes, final @NotNull SentryAndroidOptions options) {
    try (final @NotNull ISentryLifecycleToken ignored = watchDogLock.acquire()) {
      if (anrWatchDog == null) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "ANR timeout in milliseconds: %d",
                options.getAnrTimeoutIntervalMillis());

        anrWatchDog =
            new ANRWatchDog(
                options.getAnrTimeoutIntervalMillis(),
                options.isAnrReportInDebug(),
                error -> reportANR(scopes, options, error),
                options.getLogger(),
                context);
        anrWatchDog.start();

        options.getLogger().log(SentryLevel.DEBUG, "AnrIntegration installed.");
      }
    }
  }

  @TestOnly
  void reportANR(
      final @NotNull IScopes scopes,
      final @NotNull SentryAndroidOptions options,
      final @NotNull ApplicationNotResponding error) {
    options.getLogger().log(SentryLevel.INFO, "ANR triggered with message: %s", error.getMessage());

    // if LifecycleWatcher isn't available, we always assume the ANR is foreground
    final boolean isAppInBackground = Boolean.TRUE.equals(AppState.getInstance().isInBackground());

    @SuppressWarnings("ThrowableNotThrown")
    final Throwable anrThrowable = buildAnrThrowable(isAppInBackground, options, error);

    final SentryEvent event = new SentryEvent(anrThrowable);
    event.setLevel(SentryLevel.ERROR);

    final AnrHint anrHint = new AnrHint(isAppInBackground);
    final Hint hint = HintUtils.createWithTypeCheckHint(anrHint);
    scopes.captureEvent(event, hint);
  }

  private @NotNull Throwable buildAnrThrowable(
      final boolean isAppInBackground,
      final @NotNull SentryAndroidOptions options,
      final @NotNull ApplicationNotResponding anr) {

    String message = "ANR for at least " + options.getAnrTimeoutIntervalMillis() + " ms.";
    if (isAppInBackground) {
      message = "Background " + message;
    }

    final @Nullable Thread thread = anr.getThread();
    final @NotNull ApplicationNotResponding error;
    if (thread == null) {
      error = new ApplicationNotResponding(message);
    } else {
      error = new ApplicationNotResponding(message, anr.getThread());
    }

    final Mechanism mechanism = new Mechanism();
    mechanism.setType("ANR");

    return new ExceptionMechanismException(mechanism, error, thread, true);
  }

  @TestOnly
  @Nullable
  ANRWatchDog getANRWatchDog() {
    return anrWatchDog;
  }

  @Override
  public void close() throws IOException {
    try (final @NotNull ISentryLifecycleToken ignored = startLock.acquire()) {
      isClosed = true;
    }
    try (final @NotNull ISentryLifecycleToken ignored = watchDogLock.acquire()) {
      if (anrWatchDog != null) {
        anrWatchDog.interrupt();
        anrWatchDog = null;
        if (options != null) {
          options.getLogger().log(SentryLevel.DEBUG, "AnrIntegration removed.");
        }
      }
    }
  }

  /**
   * ANR is an abnormal session exit, according to <a
   * href="https://develop.sentry.dev/sdk/sessions/#crashed-abnormal-vs-errored">Develop Docs</a>
   * because we don't know whether the app has recovered after it or not.
   */
  static final class AnrHint implements AbnormalExit, TransactionEnd {

    private final boolean isBackgroundAnr;

    AnrHint(final boolean isBackgroundAnr) {
      this.isBackgroundAnr = isBackgroundAnr;
    }

    @Override
    public String mechanism() {
      return isBackgroundAnr ? "anr_background" : "anr_foreground";
    }

    // We don't want the current thread (watchdog) to be marked as crashed, otherwise the Sentry
    // Console prioritizes it over the main thread in the thread's list.
    @Override
    public boolean ignoreCurrentThread() {
      return true;
    }

    @Override
    public @Nullable Long timestamp() {
      return null;
    }
  }
}
