package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AnrTypes;
import android.app.AnrWarningResult;
import android.content.Context;
import android.os.Build;
import androidx.annotation.RequiresApi;
import io.sentry.Breadcrumb;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.ExceptionUtils;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Tracks the application lifecycle to drive session tracking and adds the following breadcrumbs:
 *
 * <ul>
 *   <li>{@code app.lifecycle}: the app moved to the foreground or background
 *   <li>{@code app.anr}: the system reported an ANR warning (Android 17+)
 * </ul>
 */
public final class AppLifecycleIntegration implements Integration, Closeable {

  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();
  @TestOnly @Nullable volatile LifecycleWatcher watcher;

  private @Nullable SentryAndroidOptions options;

  // Both null when created through the deprecated no-arg constructor, which skips ANR warnings.
  private final @Nullable Context context;
  private final @Nullable BuildInfoProvider buildInfoProvider;
  private @Nullable Consumer<AnrWarningResult> anrWarningConsumer;
  private boolean isClosed;

  /**
   * @deprecated Without a {@link Context} the integration cannot listen for ANR warnings. Use
   *     {@link #AppLifecycleIntegration(Context, BuildInfoProvider)} instead.
   */
  @Deprecated
  public AppLifecycleIntegration() {
    this.context = null;
    this.buildInfoProvider = null;
  }

  public AppLifecycleIntegration(
      final @NotNull Context context, final @NotNull BuildInfoProvider buildInfoProvider) {
    this.context = ContextUtils.getApplicationContext(context);
    this.buildInfoProvider = buildInfoProvider;
  }

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    Objects.requireNonNull(scopes, "Scopes are required");
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "enableSessionTracking enabled: %s",
            this.options.isEnableAutoSessionTracking());

    this.options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "enableAppLifecycleBreadcrumbs enabled: %s",
            this.options.isEnableAppLifecycleBreadcrumbs());

    if (this.options.isEnableAutoSessionTracking()
        || this.options.isEnableAppLifecycleBreadcrumbs()) {
      try (final ISentryLifecycleToken ignored = lock.acquire()) {
        if (watcher != null) {
          return;
        }

        watcher =
            new LifecycleWatcher(
                scopes,
                this.options.getSessionTrackingIntervalMillis(),
                this.options.isEnableAutoSessionTracking(),
                this.options.isEnableAppLifecycleBreadcrumbs());

        AppState.getInstance().addAppStateListener(watcher);
      }

      if (this.options.isEnableAppLifecycleBreadcrumbs()) {
        scheduleRegisterAnrWarningListener(scopes, this.options);
      }

      options.getLogger().log(SentryLevel.DEBUG, "AppLifecycleIntegration installed.");
      addIntegrationToSdkVersion("AppLifecycle");
    }
  }

  private void removeObservers() {
    final @Nullable LifecycleWatcher watcherRef;
    try (final ISentryLifecycleToken ignored = lock.acquire()) {
      // Set before scheduling the unregister, so a register task that is still queued bails out
      // instead of resurrecting the listener after close.
      isClosed = true;
      watcherRef = watcher;
      watcher = null;
    }

    if (watcherRef != null) {
      AppState.getInstance().removeAppStateListener(watcherRef);
      if (options != null) {
        options.getLogger().log(SentryLevel.DEBUG, "AppLifecycleIntegration removed.");
      }
    }

    scheduleUnregisterAnrWarningListener();
  }

  /**
   * {@link ActivityManager#registerAnrWarningListener} is a binder call, so it must not run on the
   * caller's thread — {@link #register} is driven from {@code Sentry.init} on the main thread.
   */
  private void scheduleRegisterAnrWarningListener(
      final @NotNull IScopes scopes, final @NotNull SentryAndroidOptions options) {
    final @Nullable Context context = this.context;
    if (context == null || buildInfoProvider == null) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "ANR warning listener needs a Context, not installed.");
      return;
    }
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.CINNAMON_BUN) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "ANR warning listener needs Android API 37 and above.");
      return;
    }

    try {
      options
          .getExecutorService()
          .submit(() -> registerAnrWarningListener(context, scopes, options));
    } catch (RejectedExecutionException e) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Failed to submit the ANR warning listener registration to the executor service.",
              e);
    }
  }

  private void scheduleUnregisterAnrWarningListener() {
    final @Nullable SentryAndroidOptions options = this.options;
    if (options == null) {
      return;
    }

    // unregisterAnrWarningListener is idempotent, so running it twice after a dropped task is
    // harmless, whereas never running it would leak the listener for the process lifetime.
    try {
      final @NotNull Future<?> future =
          options.getExecutorService().submit(() -> unregisterAnrWarningListener(options));
      if (future.isCancelled()) {
        unregisterAnrWarningListener(options);
      }
    } catch (RejectedExecutionException e) {
      unregisterAnrWarningListener(options);
    }
  }

  /** Runs on the executor service. Guarded by {@link #scheduleRegisterAnrWarningListener}. */
  // Lint cannot see through buildInfoProvider, which scheduleRegisterAnrWarningListener checks
  // before this ever runs. The suppression is scoped so that register() stays lint-checked.
  @SuppressLint("NewApi")
  private void registerAnrWarningListener(
      final @NotNull Context context,
      final @NotNull IScopes scopes,
      final @NotNull SentryAndroidOptions options) {
    final @Nullable ActivityManager activityManager =
        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    if (activityManager == null) {
      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "ActivityManager is not available, ANR warning listener not installed.");
      return;
    }

    final @NotNull Consumer<AnrWarningResult> consumer =
        result -> scopes.addBreadcrumb(createAnrBreadcrumb(result));

    try (final ISentryLifecycleToken ignored = lock.acquire()) {
      if (isClosed || anrWarningConsumer != null) {
        return;
      }
    }

    // The binder call runs without the lock, so that close() never waits for the system.
    try {
      activityManager.registerAnrWarningListener(Runnable::run, consumer);
    } catch (RuntimeException e) {
      // Broad catch, because a call into the system server can throw any RuntimeException. On
      // failure the listener is not installed, so no app.anr breadcrumbs are recorded.
      ExceptionUtils.rethrowIfFatal(e);
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Failed to register the ANR warning listener.", e);
      return;
    }

    final boolean closedDuringRegistration;
    try (final ISentryLifecycleToken ignored = lock.acquire()) {
      closedDuringRegistration = isClosed;
      if (!closedDuringRegistration) {
        anrWarningConsumer = consumer;
      }
    }
    if (closedDuringRegistration) {
      unregisterAnrWarningListener(activityManager, consumer, options);
      return;
    }

    options.getLogger().log(SentryLevel.DEBUG, "ANR warning listener installed.");
  }

  @SuppressLint("NewApi")
  private void unregisterAnrWarningListener(final @NotNull SentryAndroidOptions options) {
    final @Nullable Consumer<AnrWarningResult> consumerRef;
    try (final ISentryLifecycleToken ignored = lock.acquire()) {
      consumerRef = anrWarningConsumer;
      anrWarningConsumer = null;
    }

    final @Nullable Context context = this.context;
    if (consumerRef == null || context == null) {
      return;
    }

    final @Nullable ActivityManager activityManager =
        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    if (activityManager == null) {
      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "ActivityManager is not available, ANR warning listener not unregistered.");
      return;
    }
    unregisterAnrWarningListener(activityManager, consumerRef, options);
  }

  @SuppressLint("NewApi")
  private static void unregisterAnrWarningListener(
      final @NotNull ActivityManager activityManager,
      final @NotNull Consumer<AnrWarningResult> consumer,
      final @NotNull SentryAndroidOptions options) {
    try {
      activityManager.unregisterAnrWarningListener(consumer);
    } catch (RuntimeException e) {
      // Broad catch, because a call into the system server can throw any RuntimeException. On
      // failure the listener stays registered until the process ends.
      ExceptionUtils.rethrowIfFatal(e);
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Failed to unregister the ANR warning listener.", e);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.CINNAMON_BUN)
  static @NotNull Breadcrumb createAnrBreadcrumb(final @NotNull AnrWarningResult result) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setType("system");
    breadcrumb.setCategory("app.anr");
    breadcrumb.setLevel(SentryLevel.WARNING);
    breadcrumb.setMessage("ANR warning");
    breadcrumb.setData("anr_type", getAnrTypeName(result.getAnrType()));
    breadcrumb.setData("anr_id", result.getAnrId());
    breadcrumb.setData("timeout_ms", result.getTimeoutMillis());
    breadcrumb.setData("consumed_ms", result.getConsumedMillis());
    // The system leaves this empty for some ANR types, so do not carry a blank value.
    final @Nullable String description = result.getDescription();
    if (description != null && !description.isEmpty()) {
      breadcrumb.setData("description", description);
    }
    return breadcrumb;
  }

  // AnrTypes only holds compile-time int constants, which javac inlines, so this needs no
  // runtime API 37 symbol and stays callable (and testable) on every API level.
  @SuppressLint("NewApi")
  static @NotNull String getAnrTypeName(final int anrType) {
    switch (anrType) {
      case AnrTypes.ANR_TYPE_OTHER:
        return "other";
      case AnrTypes.ANR_TYPE_INPUT_DISPATCH_NO_FOCUSED_WINDOW:
        return "input_dispatch_no_focused_window";
      case AnrTypes.ANR_TYPE_INPUT_DISPATCH:
        return "input_dispatch";
      case AnrTypes.ANR_TYPE_BROADCAST_OF_INTENT:
        return "broadcast_of_intent";
      case AnrTypes.ANR_TYPE_START_FOREGROUND_SERVICE:
        return "start_foreground_service";
      case AnrTypes.ANR_TYPE_EXECUTE_SERVICE:
        return "execute_service";
      case AnrTypes.ANR_TYPE_CONTENT_PROVIDER_NOT_RESPONDING:
        return "content_provider_not_responding";
      case AnrTypes.ANR_TYPE_APP_TRIGGERED:
        return "app_triggered";
      case AnrTypes.ANR_TYPE_FOREGROUND_SHORT_SERVICE_TIMEOUT:
        return "foreground_short_service_timeout";
      case AnrTypes.ANR_TYPE_JOB_SERVICE_START:
        return "job_service_start";
      case AnrTypes.ANR_TYPE_APPLICATION_START:
        return "application_start";
      default:
        return "unknown (" + anrType + ")";
    }
  }

  @Override
  public void close() throws IOException {
    removeObservers();
    // TODO: probably should move it to Scopes.close(), but that'd require a new interface and
    //  different implementations for Java and Android. This is probably fine like this too, because
    //  integrations are closed in the same place
    AppState.getInstance().unregisterLifecycleObserver();
  }
}
