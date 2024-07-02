package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import io.sentry.Breadcrumb;
import io.sentry.DateUtils;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.ILogger;
import io.sentry.Integration;
import io.sentry.SentryDateProvider;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.TypeCheckHint;
import io.sentry.android.core.internal.util.AndroidConnectionStatusProvider;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class NetworkBreadcrumbsIntegration implements Integration, Closeable {

  private final @NotNull Context context;

  private final @NotNull BuildInfoProvider buildInfoProvider;

  private final @NotNull ILogger logger;

  @TestOnly @Nullable NetworkBreadcrumbsNetworkCallback networkCallback;

  public NetworkBreadcrumbsIntegration(
      final @NotNull Context context,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull ILogger logger) {
    this.context = Objects.requireNonNull(context, "Context is required");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
    this.logger = Objects.requireNonNull(logger, "ILogger is required");
  }

  @SuppressLint("NewApi")
  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    Objects.requireNonNull(hub, "Hub is required");
    SentryAndroidOptions androidOptions =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    logger.log(
        SentryLevel.DEBUG,
        "NetworkBreadcrumbsIntegration enabled: %s",
        androidOptions.isEnableNetworkEventBreadcrumbs());

    if (androidOptions.isEnableNetworkEventBreadcrumbs()) {

      // The specific error is logged in the ConnectivityChecker method
      if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP) {
        networkCallback = null;
        logger.log(SentryLevel.DEBUG, "NetworkBreadcrumbsIntegration requires Android 5+");
        return;
      }

      networkCallback =
          new NetworkBreadcrumbsNetworkCallback(hub, buildInfoProvider, options.getDateProvider());
      final boolean registered =
          AndroidConnectionStatusProvider.registerNetworkCallback(
              context, logger, buildInfoProvider, networkCallback);

      // The specific error is logged in the ConnectivityChecker method
      if (!registered) {
        networkCallback = null;
        logger.log(SentryLevel.DEBUG, "NetworkBreadcrumbsIntegration not installed.");
        return;
      }
      logger.log(SentryLevel.DEBUG, "NetworkBreadcrumbsIntegration installed.");
      addIntegrationToSdkVersion(getClass());
    }
  }

  @Override
  public void close() throws IOException {
    if (networkCallback != null) {
      AndroidConnectionStatusProvider.unregisterNetworkCallback(
          context, logger, buildInfoProvider, networkCallback);
      logger.log(SentryLevel.DEBUG, "NetworkBreadcrumbsIntegration remove.");
    }
    networkCallback = null;
  }

  @SuppressLint("ObsoleteSdkInt")
  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  static final class NetworkBreadcrumbsNetworkCallback extends ConnectivityManager.NetworkCallback {
    final @NotNull IHub hub;
    final @NotNull BuildInfoProvider buildInfoProvider;

    @Nullable Network currentNetwork = null;

    @Nullable NetworkCapabilities lastCapabilities = null;
    long lastCapabilityNanos = 0;
    final @NotNull SentryDateProvider dateProvider;

    NetworkBreadcrumbsNetworkCallback(
        final @NotNull IHub hub,
        final @NotNull BuildInfoProvider buildInfoProvider,
        final @NotNull SentryDateProvider dateProvider) {
      this.hub = Objects.requireNonNull(hub, "Hub is required");
      this.buildInfoProvider =
          Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
      this.dateProvider = Objects.requireNonNull(dateProvider, "SentryDateProvider is required");
    }

    @Override
    public void onAvailable(final @NonNull Network network) {
      if (network.equals(currentNetwork)) {
        return;
      }
      final Breadcrumb breadcrumb = createBreadcrumb("NETWORK_AVAILABLE");
      hub.addBreadcrumb(breadcrumb);
      currentNetwork = network;
      lastCapabilities = null;
    }

    @Override
    public void onCapabilitiesChanged(
        final @NonNull Network network, final @NonNull NetworkCapabilities networkCapabilities) {
      if (!network.equals(currentNetwork)) {
        return;
      }
      final long nowNanos = dateProvider.now().nanoTimestamp();
      final @Nullable NetworkBreadcrumbConnectionDetail connectionDetail =
          getNewConnectionDetails(
              lastCapabilities, networkCapabilities, lastCapabilityNanos, nowNanos);
      if (connectionDetail == null) {
        return;
      }
      lastCapabilities = networkCapabilities;
      lastCapabilityNanos = nowNanos;
      final Breadcrumb breadcrumb = createBreadcrumb("NETWORK_CAPABILITIES_CHANGED");
      breadcrumb.setData("download_bandwidth", connectionDetail.downBandwidth);
      breadcrumb.setData("upload_bandwidth", connectionDetail.upBandwidth);
      breadcrumb.setData("vpn_active", connectionDetail.isVpn);
      breadcrumb.setData("network_type", connectionDetail.type);
      if (connectionDetail.signalStrength != 0) {
        breadcrumb.setData("signal_strength", connectionDetail.signalStrength);
      }
      Hint hint = new Hint();
      hint.set(TypeCheckHint.ANDROID_NETWORK_CAPABILITIES, connectionDetail);
      hub.addBreadcrumb(breadcrumb, hint);
    }

    @Override
    public void onLost(final @NonNull Network network) {
      if (!network.equals(currentNetwork)) {
        return;
      }
      final Breadcrumb breadcrumb = createBreadcrumb("NETWORK_LOST");
      hub.addBreadcrumb(breadcrumb);
      currentNetwork = null;
      lastCapabilities = null;
    }

    private Breadcrumb createBreadcrumb(String action) {
      final Breadcrumb breadcrumb = new Breadcrumb();
      breadcrumb.setType("system");
      breadcrumb.setCategory("network.event");
      breadcrumb.setData("action", action);
      breadcrumb.setLevel(SentryLevel.INFO);
      return breadcrumb;
    }

    private @Nullable NetworkBreadcrumbConnectionDetail getNewConnectionDetails(
        final @Nullable NetworkCapabilities oldCapabilities,
        final @NotNull NetworkCapabilities newCapabilities,
        final long oldCapabilityNanos,
        final long newCapabilityNanos) {
      if (oldCapabilities == null) {
        return new NetworkBreadcrumbConnectionDetail(
            newCapabilities, buildInfoProvider, newCapabilityNanos);
      }
      NetworkBreadcrumbConnectionDetail oldConnectionDetails =
          new NetworkBreadcrumbConnectionDetail(
              oldCapabilities, buildInfoProvider, oldCapabilityNanos);
      NetworkBreadcrumbConnectionDetail newConnectionDetails =
          new NetworkBreadcrumbConnectionDetail(
              newCapabilities, buildInfoProvider, newCapabilityNanos);

      // We compare the details and if they are similar we return null, so that we don't spam the
      // user with lots of breadcrumbs for e.g. an increase of signal strength of 1 point
      if (oldConnectionDetails.isSimilar(newConnectionDetails)) {
        return null;
      }
      return newConnectionDetails;
    }
  }

  static class NetworkBreadcrumbConnectionDetail {
    final int downBandwidth, upBandwidth, signalStrength;
    private long timestampNanos;
    final boolean isVpn;
    final @NotNull String type;

    @SuppressLint({"NewApi", "ObsoleteSdkInt"})
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    NetworkBreadcrumbConnectionDetail(
        final @NotNull NetworkCapabilities networkCapabilities,
        final @NotNull BuildInfoProvider buildInfoProvider,
        final long capabilityNanos) {
      Objects.requireNonNull(networkCapabilities, "NetworkCapabilities is required");
      Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
      this.downBandwidth = networkCapabilities.getLinkDownstreamBandwidthKbps();
      this.upBandwidth = networkCapabilities.getLinkUpstreamBandwidthKbps();
      int strength =
          buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.Q
              ? networkCapabilities.getSignalStrength()
              : 0;
      // If the system reports a signalStrength of Integer.MIN_VALUE, we adjust it to be 0
      this.signalStrength = strength > -100 ? strength : 0;
      this.isVpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
      String connectionType =
          AndroidConnectionStatusProvider.getConnectionType(networkCapabilities, buildInfoProvider);
      this.type = connectionType != null ? connectionType : "";
      this.timestampNanos = capabilityNanos;
    }

    /**
     * Compares this connection detail to another one.
     *
     * @param other The other NetworkBreadcrumbConnectionDetail to compare
     * @return true if the details are similar enough, false otherwise
     */
    boolean isSimilar(final @NotNull NetworkBreadcrumbConnectionDetail other) {
      int signalDiff = Math.abs(signalStrength - other.signalStrength);
      int downBandwidthDiff = Math.abs(downBandwidth - other.downBandwidth);
      int upBandwidthDiff = Math.abs(upBandwidth - other.upBandwidth);
      // Signal and bandwidth will be reported at most once every 5 seconds.
      // This means that if the new connection detail come less than 5 seconds after the previous
      //  one, we'll report the signal and bandwidth as similar, regardless of their real value.
      boolean isTimestampSimilar =
          DateUtils.nanosToMillis(Math.abs(timestampNanos - other.timestampNanos)) < 5000;
      boolean isSignalSimilar = isTimestampSimilar || signalDiff <= 5;
      boolean isDownBandwidthSimilar =
          isTimestampSimilar || downBandwidthDiff <= Math.max(1000, Math.abs(downBandwidth) * 0.1);
      boolean isUpBandwidthSimilar =
          isTimestampSimilar || upBandwidthDiff <= Math.max(1000, Math.abs(upBandwidth) * 0.1);
      return isVpn == other.isVpn
          && type.equals(other.type)
          && isSignalSimilar
          && isDownBandwidthSimilar
          && isUpBandwidthSimilar;
    }
  }
}
