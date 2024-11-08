package io.sentry.android.core;

import static android.Manifest.permission.READ_PHONE_STATE;
import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.content.Context;
import android.telephony.TelephonyManager;
import io.sentry.Breadcrumb;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.internal.util.Permissions;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class PhoneStateBreadcrumbsIntegration implements Integration, Closeable {

  private final @NotNull Context context;
  private @Nullable SentryAndroidOptions options;
  @TestOnly @Nullable PhoneStateChangeListener listener;
  private @Nullable TelephonyManager telephonyManager;
  private boolean isClosed = false;
  private final @NotNull AutoClosableReentrantLock startLock = new AutoClosableReentrantLock();

  public PhoneStateBreadcrumbsIntegration(final @NotNull Context context) {
    this.context =
        Objects.requireNonNull(ContextUtils.getApplicationContext(context), "Context is required");
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
            "enableSystemEventBreadcrumbs enabled: %s",
            this.options.isEnableSystemEventBreadcrumbs());

    if (this.options.isEnableSystemEventBreadcrumbs()
        && Permissions.hasPermission(context, READ_PHONE_STATE)) {
      try {
        options
            .getExecutorService()
            .submit(
                () -> {
                  try (final @NotNull ISentryLifecycleToken ignored = startLock.acquire()) {
                    if (!isClosed) {
                      startTelephonyListener(scopes, options);
                    }
                  }
                });
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Failed to start PhoneStateBreadcrumbsIntegration on executor thread.",
                e);
      }
    }
  }

  @SuppressWarnings("deprecation")
  private void startTelephonyListener(
      final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    if (telephonyManager != null) {
      try {
        listener = new PhoneStateChangeListener(scopes);
        telephonyManager.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE);

        options.getLogger().log(SentryLevel.DEBUG, "PhoneStateBreadcrumbsIntegration installed.");
        addIntegrationToSdkVersion("PhoneStateBreadcrumbs");
      } catch (Throwable e) {
        options
            .getLogger()
            .log(SentryLevel.INFO, e, "TelephonyManager is not available or ready to use.");
      }
    } else {
      options.getLogger().log(SentryLevel.INFO, "TelephonyManager is not available");
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void close() throws IOException {
    try (final @NotNull ISentryLifecycleToken ignored = startLock.acquire()) {
      isClosed = true;
    }
    if (telephonyManager != null && listener != null) {
      telephonyManager.listen(listener, android.telephony.PhoneStateListener.LISTEN_NONE);
      listener = null;

      if (options != null) {
        options.getLogger().log(SentryLevel.DEBUG, "PhoneStateBreadcrumbsIntegration removed.");
      }
    }
  }

  @SuppressWarnings("deprecation")
  static final class PhoneStateChangeListener extends android.telephony.PhoneStateListener {

    private final @NotNull IScopes scopes;

    PhoneStateChangeListener(final @NotNull IScopes scopes) {
      this.scopes = scopes;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCallStateChanged(int state, String incomingNumber) {
      // incomingNumber is never used and it's always empty if you don't have permission:
      // android.permission.READ_CALL_LOG
      if (state == TelephonyManager.CALL_STATE_RINGING) {
        final Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setType("system");
        breadcrumb.setCategory("device.event");
        breadcrumb.setData("action", "CALL_STATE_RINGING");
        breadcrumb.setMessage("Device ringing");
        breadcrumb.setLevel(SentryLevel.INFO);
        scopes.addBreadcrumb(breadcrumb);
      }
    }
  }
}
