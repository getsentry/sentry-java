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
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.ILogger;
import io.sentry.Integration;
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

      networkCallback = new NetworkBreadcrumbsNetworkCallback(hub, buildInfoProvider);
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

    NetworkBreadcrumbsNetworkCallback(
        final @NotNull IHub hub, final @NotNull BuildInfoProvider buildInfoProvider) {
      this.hub = Objects.requireNonNull(hub, "Hub is required");
      this.buildInfoProvider =
          Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
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
      final @Nullable NetworkBreadcrumbConnectionDetail connectionDetail =
          getNewConnectionDetails(lastCapabilities, networkCapabilities);
      if (connectionDetail == null) {
        return;
      }
      lastCapabilities = networkCapabilities;
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
        final @NotNull NetworkCapabilities newCapabilities) {
      if (oldCapabilities == null) {
        return new NetworkBreadcrumbConnectionDetail(newCapabilities, buildInfoProvider);
      }
      NetworkBreadcrumbConnectionDetail oldConnectionDetails =
          new NetworkBreadcrumbConnectionDetail(oldCapabilities, buildInfoProvider);
      NetworkBreadcrumbConnectionDetail newConnectionDetails =
          new NetworkBreadcrumbConnectionDetail(newCapabilities, buildInfoProvider);

      // We compare the details and if they are similar we return null, so that we don't spam the
      // user with lots of breadcrumbs for e.g. an increase of signal strength of 1 point
      if (newConnectionDetails.isSimilar(oldConnectionDetails)) {
        return null;
      }
      return newConnectionDetails;
    }
  }

  static class NetworkBreadcrumbConnectionDetail {
    final int downBandwidth, upBandwidth, signalStrength;
    final boolean isVpn;
    final @NotNull String type;

    @SuppressLint({"NewApi", "ObsoleteSdkInt"})
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    NetworkBreadcrumbConnectionDetail(
        final @NotNull NetworkCapabilities networkCapabilities,
        final @NotNull BuildInfoProvider buildInfoProvider) {
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
    }

    /**
     * Compares this connection detail to another one.
     *
     * @param other The other NetworkBreadcrumbConnectionDetail to compare
     * @return true if the details are similar enough, false otherwise
     */
    boolean isSimilar(final @NotNull NetworkBreadcrumbConnectionDetail other) {
      return isVpn == other.isVpn
          && type.equals(other.type)
          && (-5 <= signalStrength - other.signalStrength
              && signalStrength - other.signalStrength <= 5)
          && (-1000 <= downBandwidth - other.downBandwidth
              && downBandwidth - other.downBandwidth <= 1000)
          && (-1000 <= upBandwidth - other.upBandwidth && upBandwidth - other.upBandwidth <= 1000);
    }
  }
}
