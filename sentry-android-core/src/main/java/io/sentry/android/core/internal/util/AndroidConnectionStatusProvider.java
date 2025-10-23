package io.sentry.android.core.internal.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import io.sentry.IConnectionStatusProvider;
import io.sentry.ILogger;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.AppState;
import io.sentry.android.core.BuildInfoProvider;
import io.sentry.android.core.ContextUtils;
import io.sentry.transport.ICurrentDateProvider;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
public final class AndroidConnectionStatusProvider
    implements IConnectionStatusProvider, AppState.AppStateListener {

  private final @NotNull Context context;
  private final @NotNull SentryOptions options;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull ICurrentDateProvider timeProvider;
  private final @NotNull List<IConnectionStatusObserver> connectionStatusObservers;
  private final @Nullable Handler handler;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();
  private volatile @Nullable NetworkCallback networkCallback;

  private static final @NotNull AutoClosableReentrantLock connectivityManagerLock =
      new AutoClosableReentrantLock();
  private static volatile @Nullable ConnectivityManager connectivityManager;

  private static final @NotNull AutoClosableReentrantLock childCallbacksLock =
      new AutoClosableReentrantLock();
  private static final @NotNull List<NetworkCallback> childCallbacks = new ArrayList<>();
  private static final AtomicBoolean isUpdatingCache = new AtomicBoolean(false);

  private static final int[] transports = {
    NetworkCapabilities.TRANSPORT_WIFI,
    NetworkCapabilities.TRANSPORT_CELLULAR,
    NetworkCapabilities.TRANSPORT_ETHERNET,
    NetworkCapabilities.TRANSPORT_BLUETOOTH
  };

  private static final int[] capabilities = new int[2];

  private volatile @Nullable NetworkCapabilities cachedNetworkCapabilities;
  private volatile @Nullable Network currentNetwork;
  private volatile long lastCacheUpdateTime = 0;
  private static final long CACHE_TTL_MS = 2 * 60 * 1000L; // 2 minutes
  private final @NotNull AtomicBoolean isConnected = new AtomicBoolean(false);

  public AndroidConnectionStatusProvider(
      @NotNull Context context,
      @NotNull SentryOptions options,
      @NotNull BuildInfoProvider buildInfoProvider,
      @NotNull ICurrentDateProvider timeProvider) {
    this(context, options, buildInfoProvider, timeProvider, null);
  }

  @SuppressLint("InlinedApi")
  public AndroidConnectionStatusProvider(
      @NotNull Context context,
      @NotNull SentryOptions options,
      @NotNull BuildInfoProvider buildInfoProvider,
      @NotNull ICurrentDateProvider timeProvider,
      @Nullable Handler handler) {
    this.context = ContextUtils.getApplicationContext(context);
    this.options = options;
    this.buildInfoProvider = buildInfoProvider;
    this.timeProvider = timeProvider;
    this.handler = handler;
    this.connectionStatusObservers = new ArrayList<>();

    capabilities[0] = NetworkCapabilities.NET_CAPABILITY_INTERNET;
    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.M) {
      capabilities[1] = NetworkCapabilities.NET_CAPABILITY_VALIDATED;
    }

    // Register network callback immediately for caching
    //noinspection Convert2MethodRef
    submitSafe(() -> ensureNetworkCallbackRegistered());

    AppState.getInstance().addAppStateListener(this);
  }

  /**
   * Enhanced network connectivity check for Android 15. Checks for NET_CAPABILITY_INTERNET,
   * NET_CAPABILITY_VALIDATED, and proper transport types.
   * https://medium.com/@doronkakuli/adapting-your-network-connectivity-checks-for-android-15-a-practical-guide-2b1850619294
   */
  @SuppressLint("InlinedApi")
  private boolean isNetworkEffectivelyConnected(
      final @Nullable NetworkCapabilities networkCapabilities) {
    if (networkCapabilities == null) {
      return false;
    }

    // Check for general internet capability AND validated status
    boolean hasInternetAndValidated =
        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.M) {
      hasInternetAndValidated =
          hasInternetAndValidated
              && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    if (!hasInternetAndValidated) {
      return false;
    }

    // Additionally, ensure it's a recognized transport type for general internet access
    for (final int transport : transports) {
      if (networkCapabilities.hasTransport(transport)) {
        return true;
      }
    }
    return false;
  }

  /** Get connection status from cached NetworkCapabilities or fallback to legacy method. */
  private @NotNull ConnectionStatus getConnectionStatusFromCache() {
    if (cachedNetworkCapabilities != null) {
      return isNetworkEffectivelyConnected(cachedNetworkCapabilities)
          ? ConnectionStatus.CONNECTED
          : ConnectionStatus.DISCONNECTED;
    }

    // Fallback to legacy method when NetworkCapabilities not available
    final ConnectivityManager connectivityManager =
        getConnectivityManager(context, options.getLogger());
    if (connectivityManager != null) {
      return getConnectionStatus(context, connectivityManager, options.getLogger());
    }

    return ConnectionStatus.UNKNOWN;
  }

  /** Get connection type from cached NetworkCapabilities or fallback to legacy method. */
  private @Nullable String getConnectionTypeFromCache() {
    final NetworkCapabilities capabilities = cachedNetworkCapabilities;
    if (capabilities != null) {
      return getConnectionType(capabilities);
    }

    // Fallback to legacy method when NetworkCapabilities not available
    return getConnectionType(context, options.getLogger(), buildInfoProvider);
  }

  private void ensureNetworkCallbackRegistered() {
    if (!ContextUtils.isForegroundImportance()) {
      return;
    }

    if (networkCallback != null) {
      return; // Already registered
    }

    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (networkCallback != null) {
        return;
      }

      final @NotNull NetworkCallback callback =
          new NetworkCallback() {
            @Override
            public void onAvailable(final @NotNull Network network) {
              currentNetwork = network;

              // have to only dispatch this on first registration + when the connection got
              // re-established
              // otherwise it would've been dispatched on every foreground
              if (!isConnected.getAndSet(true)) {
                try (final @NotNull ISentryLifecycleToken ignored = childCallbacksLock.acquire()) {
                  for (final @NotNull NetworkCallback cb : childCallbacks) {
                    cb.onAvailable(network);
                  }
                }
              }
            }

            @RequiresApi(Build.VERSION_CODES.O)
            @Override
            public void onUnavailable() {
              clearCacheAndNotifyObservers();

              try (final @NotNull ISentryLifecycleToken ignored = childCallbacksLock.acquire()) {
                for (final @NotNull NetworkCallback cb : childCallbacks) {
                  cb.onUnavailable();
                }
              }
            }

            @Override
            public void onLost(final @NotNull Network network) {
              if (!network.equals(currentNetwork)) {
                return;
              }
              clearCacheAndNotifyObservers();

              try (final @NotNull ISentryLifecycleToken ignored = childCallbacksLock.acquire()) {
                for (final @NotNull NetworkCallback cb : childCallbacks) {
                  cb.onLost(network);
                }
              }
            }

            private void clearCacheAndNotifyObservers() {
              isConnected.set(false);
              // Clear cached capabilities and network reference atomically
              try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
                cachedNetworkCapabilities = null;
                currentNetwork = null;
                lastCacheUpdateTime = timeProvider.getCurrentTimeMillis();

                options
                    .getLogger()
                    .log(SentryLevel.DEBUG, "Cache cleared - network lost/unavailable");

                // Notify all observers with DISCONNECTED status directly
                // No need to query ConnectivityManager - we know the network is gone
                for (final @NotNull IConnectionStatusObserver observer :
                    connectionStatusObservers) {
                  observer.onConnectionStatusChanged(ConnectionStatus.DISCONNECTED);
                }
              }
            }

            @Override
            public void onCapabilitiesChanged(
                @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
              if (!network.equals(currentNetwork)) {
                return;
              }
              updateCacheAndNotifyObservers(network, networkCapabilities);

              try (final @NotNull ISentryLifecycleToken ignored = childCallbacksLock.acquire()) {
                for (final @NotNull NetworkCallback cb : childCallbacks) {
                  cb.onCapabilitiesChanged(network, networkCapabilities);
                }
              }
            }

            private void updateCacheAndNotifyObservers(
                @Nullable Network network, @Nullable NetworkCapabilities networkCapabilities) {
              // Check if this change is meaningful before notifying observers
              final boolean shouldUpdate = isSignificantChange(networkCapabilities);

              // Only notify observers if something meaningful changed
              if (shouldUpdate) {
                try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
                cachedNetworkCapabilities = networkCapabilities;
                lastCacheUpdateTime = timeProvider.getCurrentTimeMillis();
                  final @NotNull ConnectionStatus status = getConnectionStatusFromCache();
                options
                    .getLogger()
                    .log(
                        SentryLevel.DEBUG,
                        "Cache updated - Status: " + status + ", Type: " + getConnectionTypeFromCache());

                  for (final @NotNull IConnectionStatusObserver observer :
                      connectionStatusObservers) {
                    observer.onConnectionStatusChanged(status);
                  }
                }
              }
            }

            /**
             * Check if NetworkCapabilities change is significant for our observers. Only notify for
             * changes that affect connectivity status or connection type.
             */
            private boolean isSignificantChange(@Nullable NetworkCapabilities newCapabilities) {
              final NetworkCapabilities oldCapabilities = cachedNetworkCapabilities;

              // Always significant if transitioning between null and non-null
              if ((oldCapabilities == null) != (newCapabilities == null)) {
                return true;
              }

              // If both null, no change
              if (oldCapabilities == null && newCapabilities == null) {
                return false;
              }

              // Check significant capability changes
              if (hasSignificantCapabilityChanges(oldCapabilities, newCapabilities)) {
                return true;
              }

              // Check significant transport changes
              if (hasSignificantTransportChanges(oldCapabilities, newCapabilities)) {
                return true;
              }

              return false;
            }

            /** Check if capabilities that affect connectivity status changed. */
            private boolean hasSignificantCapabilityChanges(
                @NotNull NetworkCapabilities old, @NotNull NetworkCapabilities new_) {
              // Check capabilities we care about for connectivity determination
              for (int capability : capabilities) {
                if (capability != 0
                    && old.hasCapability(capability) != new_.hasCapability(capability)) {
                  return true;
                }
              }

              return false;
            }

            /** Check if transport types that affect connection type changed. */
            private boolean hasSignificantTransportChanges(
                @NotNull NetworkCapabilities old, @NotNull NetworkCapabilities new_) {
              // Check transports we care about for connection type determination
              for (int transport : transports) {
                if (old.hasTransport(transport) != new_.hasTransport(transport)) {
                  return true;
                }
              }

              return false;
            }
          };

      if (registerNetworkCallback(
          context, options.getLogger(), buildInfoProvider, handler, callback)) {
        networkCallback = callback;
        options.getLogger().log(SentryLevel.DEBUG, "Network callback registered successfully");
      } else {
        options.getLogger().log(SentryLevel.WARNING, "Failed to register network callback");
      }
    }
  }

  @SuppressLint({"NewApi", "MissingPermission"})
  private void updateCache() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      cachedNetworkCapabilities = null;
      lastCacheUpdateTime = timeProvider.getCurrentTimeMillis();
    }
    try {
      if (!Permissions.hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)) {
        options
            .getLogger()
            .log(SentryLevel.INFO, "No permission (ACCESS_NETWORK_STATE) to check network status.");
        return;
      }

      if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.M) {
        return;
      }

      // Fallback: query current active network in the background
      submitSafe(
          () -> {
            // Avoid concurrent updates
            if (!isUpdatingCache.getAndSet(true)) {
              final ConnectivityManager connectivityManager =
                getConnectivityManager(context, options.getLogger());
              if (connectivityManager != null) {
                final @Nullable NetworkCapabilities capabilities =
                  getNetworkCapabilities(connectivityManager);

                try (final @NotNull ISentryLifecycleToken ignored2 = lock.acquire()) {
                  cachedNetworkCapabilities = capabilities;
                  lastCacheUpdateTime = timeProvider.getCurrentTimeMillis();

                  if (capabilities != null) {
                    options
                      .getLogger()
                      .log(
                        SentryLevel.DEBUG,
                        "Cache updated - Status: "
                          + getConnectionStatusFromCache()
                          + ", Type: "
                          + getConnectionTypeFromCache());
                  }
                }
              }
              isUpdatingCache.set(false);
            }
          });

    } catch (Throwable t) {
      options.getLogger().log(SentryLevel.WARNING, "Failed to update connection status cache", t);
      cachedNetworkCapabilities = null;
      lastCacheUpdateTime = timeProvider.getCurrentTimeMillis();
    }
  }

  private boolean isCacheValid() {
    return (timeProvider.getCurrentTimeMillis() - lastCacheUpdateTime) < CACHE_TTL_MS;
  }

  @Override
  public @NotNull ConnectionStatus getConnectionStatus() {
    if (!isCacheValid()) {
      updateCache();
    }
    return getConnectionStatusFromCache();
  }

  @Override
  public @Nullable String getConnectionType() {
    if (!isCacheValid()) {
      updateCache();
    }
    return getConnectionTypeFromCache();
  }

  @Override
  public boolean addConnectionStatusObserver(final @NotNull IConnectionStatusObserver observer) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      connectionStatusObservers.add(observer);
    }
    ensureNetworkCallbackRegistered();

    // Network callback is already registered during initialization
    return networkCallback != null;
  }

  @Override
  public void removeConnectionStatusObserver(final @NotNull IConnectionStatusObserver observer) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      connectionStatusObservers.remove(observer);
      // Keep the callback registered for caching even if no observers
    }
  }

  private void unregisterNetworkCallback(final boolean clearObservers) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (clearObservers) {
        connectionStatusObservers.clear();
      }

      final @Nullable NetworkCallback callbackRef = networkCallback;
      networkCallback = null;

      if (callbackRef != null) {
        unregisterNetworkCallback(context, options.getLogger(), callbackRef);
      }
      // Clear cached state
      cachedNetworkCapabilities = null;
      currentNetwork = null;
      lastCacheUpdateTime = 0;
    }
    options.getLogger().log(SentryLevel.DEBUG, "Network callback unregistered");
  }

  /** Clean up resources - should be called when the provider is no longer needed */
  @Override
  public void close() {
    submitSafe(
        () -> {
          unregisterNetworkCallback(/* clearObservers= */ true);
          try (final @NotNull ISentryLifecycleToken ignored = childCallbacksLock.acquire()) {
            childCallbacks.clear();
          }
          try (final @NotNull ISentryLifecycleToken ignored = connectivityManagerLock.acquire()) {
            connectivityManager = null;
          }
          AppState.getInstance().removeAppStateListener(this);
        });
  }

  @Override
  public void onForeground() {
    if (networkCallback != null) {
      return;
    }

    submitSafe(
        () -> {
          // proactively update cache and notify observers on foreground to ensure connectivity
          // state is not stale
          updateCache();

          final @NotNull ConnectionStatus status = getConnectionStatusFromCache();
          if (status == ConnectionStatus.DISCONNECTED) {
            // onLost is not called retroactively when we registerNetworkCallback (as opposed to
            // onAvailable), so we have to do it manually for the DISCONNECTED case
            isConnected.set(false);
            try (final @NotNull ISentryLifecycleToken ignored = childCallbacksLock.acquire()) {
              for (final @NotNull NetworkCallback cb : childCallbacks) {
                //noinspection DataFlowIssue
                cb.onLost(null);
              }
            }
          }
          try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
            for (final @NotNull IConnectionStatusObserver observer : connectionStatusObservers) {
              observer.onConnectionStatusChanged(status);
            }
          }

          // this will ONLY do the necessary parts like registerNetworkCallback and onAvailable, but
          // it won't updateCache and notify observes because we just did it above and the cached
          // capabilities will be the same
          ensureNetworkCallbackRegistered();
        });
  }

  @Override
  public void onBackground() {
    if (networkCallback == null) {
      return;
    }

    submitSafe(
        () -> {
          //noinspection Convert2MethodRef
          unregisterNetworkCallback(/* clearObservers= */ false);
        });
  }

  /**
   * Get the cached NetworkCapabilities for advanced use cases. Returns null if cache is stale or no
   * capabilities are available.
   *
   * @return cached NetworkCapabilities or null
   */
  @TestOnly
  @Nullable
  public NetworkCapabilities getCachedNetworkCapabilities() {
    return cachedNetworkCapabilities;
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

  @RequiresApi(Build.VERSION_CODES.M)
  @SuppressLint("MissingPermission")
  private static @Nullable NetworkCapabilities getNetworkCapabilities(
      final @NotNull ConnectivityManager connectivityManager) {
    final Network activeNetwork = connectivityManager.getActiveNetwork();
    return activeNetwork != null ? connectivityManager.getNetworkCapabilities(activeNetwork) : null;
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
        final NetworkCapabilities networkCapabilities = getNetworkCapabilities(connectivityManager);
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
    if (connectivityManager != null) {
      return connectivityManager;
    }

    try (final @NotNull ISentryLifecycleToken ignored = connectivityManagerLock.acquire()) {
      if (connectivityManager != null) {
        return connectivityManager; // Double-checked locking
      }

      connectivityManager =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (connectivityManager == null) {
        logger.log(SentryLevel.INFO, "ConnectivityManager is null and cannot check network status");
      }
      return connectivityManager;
    }
  }

  public static boolean addNetworkCallback(
      final @NotNull Context context,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull NetworkCallback networkCallback) {
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.N) {
      logger.log(SentryLevel.DEBUG, "NetworkCallbacks need Android N+.");
      return false;
    }

    if (!Permissions.hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)) {
      logger.log(SentryLevel.INFO, "No permission (ACCESS_NETWORK_STATE) to check network status.");
      return false;
    }

    try (final @NotNull ISentryLifecycleToken ignored = childCallbacksLock.acquire()) {
      childCallbacks.add(networkCallback);
    }
    return true;
  }

  public static void removeNetworkCallback(final @NotNull NetworkCallback networkCallback) {
    try (final @NotNull ISentryLifecycleToken ignored = childCallbacksLock.acquire()) {
      childCallbacks.remove(networkCallback);
    }
  }

  @SuppressLint({"MissingPermission", "NewApi"})
  static boolean registerNetworkCallback(
      final @NotNull Context context,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @Nullable Handler handler,
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
      if (handler != null) {
        connectivityManager.registerDefaultNetworkCallback(networkCallback, handler);
      } else {
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
      }
    } catch (Throwable t) {
      logger.log(SentryLevel.WARNING, "registerDefaultNetworkCallback failed", t);
      return false;
    }
    return true;
  }

  @SuppressLint("NewApi")
  static void unregisterNetworkCallback(
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

  @TestOnly
  @NotNull
  public static List<NetworkCallback> getChildCallbacks() {
    return childCallbacks;
  }

  private void submitSafe(@NotNull Runnable r) {
    try {
      options.getExecutorService().submit(r);
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "AndroidConnectionStatusProvider submit failed", e);
    }
  }
}
