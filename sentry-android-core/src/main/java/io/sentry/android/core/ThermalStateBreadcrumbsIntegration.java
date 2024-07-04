package io.sentry.android.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import androidx.annotation.RequiresApi;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ThermalStateBreadcrumbsIntegration implements Integration, Closeable {

  private final @NotNull PowerManager powerManager;
  private final @NotNull BuildInfoProvider buildInfoProvider;

  private volatile @Nullable IHub hub;
  private volatile @Nullable Integer lastThermalStatus;
  private volatile @Nullable Listener listener;

  public ThermalStateBreadcrumbsIntegration(
      final @NotNull Context context, final @NotNull BuildInfoProvider buildInfoProvider) {
    this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    this.buildInfoProvider = buildInfoProvider;
  }

  @SuppressLint("NewApi")
  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    this.hub = hub;

    final @NotNull SentryAndroidOptions androidOptions =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    if (!androidOptions.isEnableThermalStateBreadcrumbs()) {
      return;
    }

    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.Q) {
      listener = new Listener(this::onThermalStatusChanged);
      onThermalStatusChanged(powerManager.getCurrentThermalStatus());
      // noinspection DataFlowIssue
      powerManager.addThermalStatusListener(listener);
    }
  }

  @SuppressLint("NewApi")
  @Override
  public synchronized void close() throws IOException {
    hub = null;

    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.Q) {
      if (listener != null) {
        powerManager.removeThermalStatusListener(listener);
        listener = null;
      }
    }
  }

  private void onThermalStatusChanged(final @NotNull Integer status) {
    if (Objects.equals(lastThermalStatus, status)) {
      return;
    }
    lastThermalStatus = status;

    final @Nullable IHub hubInstance = hub;
    if (hubInstance != null) {
      hubInstance.addBreadcrumb(
          Breadcrumb.info("Thermal status changed: " + getThermalStatusString(status)));
    }
  }

  @NotNull
  private static String getThermalStatusString(final @Nullable Integer status) {
    if (status == null) {
      return "unknown";
    } else {
      switch (status) {
        case PowerManager.THERMAL_STATUS_NONE:
          return "none";
        case PowerManager.THERMAL_STATUS_LIGHT:
          return "light";
        case PowerManager.THERMAL_STATUS_MODERATE:
          return "moderate";
        case PowerManager.THERMAL_STATUS_SEVERE:
          return "severe";
        case PowerManager.THERMAL_STATUS_CRITICAL:
          return "critical";
        case PowerManager.THERMAL_STATUS_EMERGENCY:
          return "emergency";
        case PowerManager.THERMAL_STATUS_SHUTDOWN:
          return "shutdown";
        default:
          return "unknown";
      }
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.Q)
  private static class Listener implements PowerManager.OnThermalStatusChangedListener {

    private final @NotNull Consumer<Integer> callback;

    public Listener(final @NotNull Consumer<Integer> callback) {
      this.callback = callback;
    }

    @Override
    public void onThermalStatusChanged(int status) {
      callback.accept(status);
    }
  }
}
