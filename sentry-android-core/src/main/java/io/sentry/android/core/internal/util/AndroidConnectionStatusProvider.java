package io.sentry.android.core.internal.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import io.sentry.IConnectionStatusProvider;
import io.sentry.ILogger;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryLevel;
import io.sentry.android.core.BuildInfoProvider;
import io.sentry.android.core.ContextUtils;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Note: ConnectivityManager sometimes throws SecurityExceptions on Android 11. Hence all relevant
 * calls are guarded with try/catch. see https://issuetracker.google.com/issues/175055271 for more
 * details
 */
@ApiStatus.Internal
public final class AndroidConnectionStatusProvider implements IConnectionStatusProvider {

  private final @NotNull Context context;
  private final @NotNull ILogger logger;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull List<IConnectionStatusObserver> connectionStatusObservers;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();
  private volatile @Nullable NetworkCallback networkCallback;

  public AndroidConnectionStatusProvider(
      @NotNull Context context,
      @NotNull ILogger logger,
      @NotNull BuildInfoProvider buildInfoProvider) {
    this.context = ContextUtils.getApplicationContext(context);
    this.logger = logger;
    this.buildInfoProvider = buildInfoProvider;
    this.connectionStatusObservers = new ArrayList<>();
  }

  @Override
  public @NotNull ConnectionStatus getConnectionStatus() {
    final ConnectivityManager connectivityManager = getConnectivityManager(context, logger);
    if (connectivityManager == null) {
      return ConnectionStatus.UNKNOWN;
    }
    return getConnectionStatus(context, connectivityManager, logger);
    // getActiveNetworkInfo might return null if VPN doesn't specify its
    // underlying network

    // when min. API 24, use:
    // connectivityManager.registerDefaultNetworkCallback(...)
  }

  @Override
  public @Nullable String getConnectionType() {
    return getConnectionType(context, logger, buildInfoProvider);
  }

  @Override
  public boolean addConnectionStatusObserver(final @NotNull IConnectionStatusObserver observer) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      connectionStatusObservers.add(observer);
    }

    if (networkCallback == null) {
      try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
        if (networkCallback == null) {
          final @NotNull NetworkCallback newNetworkCallback =
              new NetworkCallback() {
                @Override
                public void onAvailable(final @NotNull Network network) {
                  updateObservers();
                }

                @Override
                public void onUnavailable() {
                  updateObservers();
                }

                @Override
                public void onLost(final @NotNull Network network) {
                  updateObservers();
                }

                public void updateObservers() {
                  final @NotNull ConnectionStatus status = getConnectionStatus();
                  try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
                    for (final @NotNull IConnectionStatusObserver observer :
                        connectionStatusObservers) {
                      observer.onConnectionStatusChanged(status);
                    }
                  }
                }
              };

          if (registerNetworkCallback(context, logger, buildInfoProvider, newNetworkCallback)) {
            networkCallback = newNetworkCallback;
            return true;
          } else {
            return false;
          }
        }
      }
    }
    // networkCallback is already registered, so we can safely return true
    return true;
  }

  @Override
  public void removeConnectionStatusObserver(final @NotNull IConnectionStatusObserver observer) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      connectionStatusObservers.remove(observer);
      if (connectionStatusObservers.isEmpty()) {
        if (networkCallback != null) {
          unregisterNetworkCallback(context, logger, networkCallback);
          networkCallback = null;
        }
      }
    }
  }

  /**
   * Return the Connection status
   *
   * @param context the Context
   * @param connectivityManager the ConnectivityManager
   * @param logger the Logger
   * @return true if connected or no permission to check, false otherwise
   */
  @SuppressWarnings({"deprecation", "MissingPermission"})
  private static @NotNull IConnectionStatusProvider.ConnectionStatus getConnectionStatus(
      final @NotNull Context context,
      final @NotNull ConnectivityManager connectivityManager,
      final @NotNull ILogger logger) {
    if (!Permissions.hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)) {
      logger.log(SentryLevel.INFO, "No permission (ACCESS_NETWORK_STATE) to check network status.");
      return ConnectionStatus.NO_PERMISSION;
    }

    try {
      final android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
      if (activeNetworkInfo == null) {
        logger.log(SentryLevel.INFO, "NetworkInfo is null, there's no active network.");
        return ConnectionStatus.DISCONNECTED;
      }
      return activeNetworkInfo.isConnected()
          ? ConnectionStatus.CONNECTED
          : ConnectionStatus.DISCONNECTED;
    } catch (Throwable t) {
      logger.log(SentryLevel.WARNING, "Could not retrieve Connection Status", t);
      return ConnectionStatus.UNKNOWN;
    }
  }

  /**
   * Check the connection type of the active network
   *
   * @param context the App. context
   * @param logger the logger from options
   * @param buildInfoProvider the BuildInfoProvider provider
   * @return the connection type wifi, ethernet, cellular or null
   */
  @SuppressLint({"ObsoleteSdkInt", "MissingPermission", "NewApi"})
  public static @Nullable String getConnectionType(
      final @NotNull Context context,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    final ConnectivityManager connectivityManager = getConnectivityManager(context, logger);
    if (connectivityManager == null) {
      return null;
    }
    if (!Permissions.hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)) {
      logger.log(SentryLevel.INFO, "No permission (ACCESS_NETWORK_STATE) to check network status.");
      return null;
    }

    try {
      boolean ethernet = false;
      boolean wifi = false;
      boolean cellular = false;

      if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.M) {

        final Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
          logger.log(SentryLevel.INFO, "Network is null and cannot check network status");
          return null;
        }
        final NetworkCapabilities networkCapabilities =
            connectivityManager.getNetworkCapabilities(activeNetwork);
        if (networkCapabilities == null) {
          logger.log(SentryLevel.INFO, "NetworkCapabilities is null and cannot check network type");
          return null;
        }
        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
          ethernet = true;
        }
        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
          wifi = true;
        }
        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
          cellular = true;
        }
      } else {
        // ideally using connectivityManager.getAllNetworks(), but its >= Android L only

        // for some reason linting didn't allow a single @SuppressWarnings("deprecation") on method
        // signature
        @SuppressWarnings("deprecation")
        final android.net.NetworkInfo activeNetworkInfo =
            connectivityManager.getActiveNetworkInfo();

        if (activeNetworkInfo == null) {
          logger.log(SentryLevel.INFO, "NetworkInfo is null, there's no active network.");
          return null;
        }

        @SuppressWarnings("deprecation")
        final int type = activeNetworkInfo.getType();

        @SuppressWarnings("deprecation")
        final int TYPE_ETHERNET = ConnectivityManager.TYPE_ETHERNET;

        @SuppressWarnings("deprecation")
        final int TYPE_WIFI = ConnectivityManager.TYPE_WIFI;

        @SuppressWarnings("deprecation")
        final int TYPE_MOBILE = ConnectivityManager.TYPE_MOBILE;

        switch (type) {
          case TYPE_ETHERNET:
            ethernet = true;
            break;
          case TYPE_WIFI:
            wifi = true;
            break;
          case TYPE_MOBILE:
            cellular = true;
            break;
        }
      }

      // TODO: change the protocol to be a list of transports as a device may have the capability of
      // multiple
      if (ethernet) {
        return "ethernet";
      }
      if (wifi) {
        return "wifi";
      }
      if (cellular) {
        return "cellular";
      }

    } catch (Throwable exception) {
      logger.log(SentryLevel.ERROR, "Failed to retrieve network info", exception);
    }

    return null;
  }

  /**
   * Check the connection type of the active network
   *
   * @param networkCapabilities the NetworkCapabilities to check the transport type
   * @return the connection type wifi, ethernet, cellular or null
   */
  public static @Nullable String getConnectionType(
      final @NotNull NetworkCapabilities networkCapabilities) {
    // TODO: change the protocol to be a list of transports as a device may have the capability of
    // multiple

    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
      return "ethernet";
    }
    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
      return "wifi";
    }
    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
      return "cellular";
    }

    return null;
  }

  private static @Nullable ConnectivityManager getConnectivityManager(
      final @NotNull Context context, final @NotNull ILogger logger) {
    final ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    if (connectivityManager == null) {
      logger.log(SentryLevel.INFO, "ConnectivityManager is null and cannot check network status");
    }
    return connectivityManager;
  }

  @SuppressLint({"MissingPermission", "NewApi"})
  public static boolean registerNetworkCallback(
      final @NotNull Context context,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull NetworkCallback networkCallback) {
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.N) {
      logger.log(SentryLevel.DEBUG, "NetworkCallbacks need Android N+.");
      return false;
    }
    final ConnectivityManager connectivityManager = getConnectivityManager(context, logger);
    if (connectivityManager == null) {
      return false;
    }
    if (!Permissions.hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)) {
      logger.log(SentryLevel.INFO, "No permission (ACCESS_NETWORK_STATE) to check network status.");
      return false;
    }
    try {
      connectivityManager.registerDefaultNetworkCallback(networkCallback);
    } catch (Throwable t) {
      logger.log(SentryLevel.WARNING, "registerDefaultNetworkCallback failed", t);
      return false;
    }
    return true;
  }

  @SuppressLint("NewApi")
  public static void unregisterNetworkCallback(
      final @NotNull Context context,
      final @NotNull ILogger logger,
      final @NotNull NetworkCallback networkCallback) {

    final ConnectivityManager connectivityManager = getConnectivityManager(context, logger);
    if (connectivityManager == null) {
      return;
    }
    try {
      connectivityManager.unregisterNetworkCallback(networkCallback);
    } catch (Throwable t) {
      logger.log(SentryLevel.WARNING, "unregisterNetworkCallback failed", t);
    }
  }

  @TestOnly
  @NotNull
  public List<IConnectionStatusObserver> getStatusObservers() {
    return connectionStatusObservers;
  }

  @TestOnly
  @Nullable
  public NetworkCallback getNetworkCallback() {
    return networkCallback;
  }
}
