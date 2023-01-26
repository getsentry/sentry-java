package io.sentry.android.core;

import static android.Manifest.permission.READ_PHONE_STATE;

import android.content.Context;
import android.telephony.TelephonyManager;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.internal.util.Permissions;
import io.sentry.protocol.SdkVersion;
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

  public PhoneStateBreadcrumbsIntegration(final @NotNull Context context) {
    this.context = Objects.requireNonNull(context, "Context is required");
  }

  @SuppressWarnings("deprecation")
  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    Objects.requireNonNull(hub, "Hub is required");
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
      telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
      if (telephonyManager != null) {
        try {
          listener = new PhoneStateChangeListener(hub);
          telephonyManager.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE);

          options.getLogger().log(SentryLevel.DEBUG, "PhoneStateBreadcrumbsIntegration installed.");
          final SdkVersion sdkVersion = this.options.getSdkVersion();
          addIntegrationToSdkVersion(sdkVersion);
        } catch (Throwable e) {
          this.options
              .getLogger()
              .log(SentryLevel.INFO, e, "TelephonyManager is not available or ready to use.");
        }
      } else {
        this.options.getLogger().log(SentryLevel.INFO, "TelephonyManager is not available");
      }
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void close() throws IOException {
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

    private final @NotNull IHub hub;

    PhoneStateChangeListener(final @NotNull IHub hub) {
      this.hub = hub;
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
        hub.addBreadcrumb(breadcrumb);
      }
    }
  }
}
