package io.sentry.android.core;

import static android.telephony.PhoneStateListener.LISTEN_CALL_STATE;
import static android.telephony.PhoneStateListener.LISTEN_NONE;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import androidx.annotation.Nullable;
import io.sentry.core.Breadcrumb;
import io.sentry.core.IHub;
import io.sentry.core.Integration;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class PhoneStateBreadcrumbsIntegration implements Integration, Closeable {

  private final @NotNull Context context;
  private @Nullable SentryAndroidOptions options;
  @TestOnly @Nullable PhoneStateChangeListener listener;
  private @Nullable TelephonyManager telephonyManager;

  public PhoneStateBreadcrumbsIntegration(final @NotNull Context context) {
    this.context = Objects.requireNonNull(context, "Context is required");
  }

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

    if (this.options.isEnableSystemEventBreadcrumbs()) {
      telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
      if (telephonyManager != null) {
        try {
          listener = new PhoneStateChangeListener(hub);
          telephonyManager.listen(listener, LISTEN_CALL_STATE);

          options.getLogger().log(SentryLevel.DEBUG, "PhoneStateBreadcrumbsIntegration installed.");
        } catch (NullPointerException e) {
          this.options
              .getLogger()
              .log(SentryLevel.INFO, e, "TelephonyManager is not ready for use.");
        }
      } else {
        this.options.getLogger().log(SentryLevel.INFO, "TelephonyManager is not available");
      }
    }
  }

  @Override
  public void close() throws IOException {
    if (telephonyManager != null && listener != null) {
      telephonyManager.listen(listener, LISTEN_NONE);
      listener = null;

      if (options != null) {
        options.getLogger().log(SentryLevel.DEBUG, "PhoneStateBreadcrumbsIntegration removed.");
      }
    }
  }

  static final class PhoneStateChangeListener extends PhoneStateListener {

    private final @NotNull IHub hub;

    PhoneStateChangeListener(final @NotNull IHub hub) {
      this.hub = hub;
    }

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
