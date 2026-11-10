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
import io.sentry.time.Deadline;
import io.sentry.time.MonotonicTicker;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
  private final @NotNull MonotonicTicker ticker;

  /**
   * Copy-on-write, so that observers can be added and removed from any thread without waiting on a
   * lock, including from inside a notification. Each notification goes to the observers present
   * when it started.
   */
  private final @NotNull List<IConnectionStatusObserver> connectionStatusObservers;

  private final @NotNull CellularNetworkTechnologyProvider cellularNetworkTechnologyProvider;
  private final @Nullable Handler handler;

  /**
   * What the provider currently knows about the default network. Replaced as a whole, never
   * modified, so readers on any thread, including the main thread when it captures an event, see a
   * consistent value without taking a lock.
   */
  private final @NotNull AtomicReference<ConnectionSnapshot> snapshot;

  /**
   * Serializes registering and unregistering the network and telephony callbacks. These are
   * synchronous calls into system services that can take a long time, so the lock is only taken on
   * the SDK executor, and nothing that callers wait for is done while it is held.
   */
  private final @NotNull AutoClosableReentrantLock registrationLock =
      new AutoClosableReentrantLock();

  private volatile @Nullable NetworkCallback networkCallback;

  /**
   * Set by {@link #close()} on the calling thread, before the unregistration it schedules runs.
   * Registration and foreground refreshes queued on the SDK executor after that unregistration
   * check it, so they don't register the callbacks again on a closed provider.
   */
  private volatile boolean closed;

  private static final @NotNull AutoClosableReentrantLock connectivityManagerLock =
      new AutoClosableReentrantLock();
  private static volatile @Nullable ConnectivityManager connectivityManager;

  /**
   * Copy-on-write, so that callbacks are dispatched from a snapshot without holding a lock. The
   * callbacks run SDK code such as adding breadcrumbs, which in turn runs user callbacks, while
   * other threads, including the main thread, add and remove callbacks.
   */
  private static final @NotNull List<NetworkCallback> childCallbacks = new CopyOnWriteArrayList<>();

  private static final int[] transports = {
    NetworkCapabilities.TRANSPORT_WIFI,
    NetworkCapabilities.TRANSPORT_CELLULAR,
    NetworkCapabilities.TRANSPORT_ETHERNET,
    NetworkCapabilities.TRANSPORT_BLUETOOTH
  };

  /**
   * The capabilities whose change affects the connection status, which depends on the API level.
   */
  private final int[] capabilityIds;

  private static final long CACHE_TTL_MINUTES = 2;

  /**
   * Flag for ensuring {@link #childCallbacks child callbacks} don't get more than one {@link
   * NetworkCallback#onAvailable} invocation per connection.
   *
   * <p>Reset when the network is lost or unavailable.
   */
  private final @NotNull AtomicBoolean onAvailableForwarded = new AtomicBoolean(false);

  public AndroidConnectionStatusProvider(
      @NotNull Context context,
      @NotNull SentryOptions options,
      @NotNull BuildInfoProvider buildInfoProvider,
      @NotNull MonotonicTicker ticker) {
    this(context, options, buildInfoProvider, ticker, null);
  }

  @SuppressLint("InlinedApi")
  public AndroidConnectionStatusProvider(
      @NotNull Context context,
      @NotNull SentryOptions options,
      @NotNull BuildInfoProvider buildInfoProvider,
      @NotNull MonotonicTicker ticker,
      @Nullable Handler handler) {
    this.context = ContextUtils.getApplicationContext(context);
    this.options = options;
    this.buildInfoProvider = buildInfoProvider;
    this.ticker = ticker;
    this.snapshot = new AtomicReference<>(ConnectionSnapshot.unknown(ticker));
    this.handler = handler;
    this.connectionStatusObservers = new CopyOnWriteArrayList<>();
    this.cellularNetworkTechnologyProvider =
        new CellularNetworkTechnologyProvider(this.context, options.getLogger(), buildInfoProvider);

    this.capabilityIds =
        buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.M
            ? new int[] {
              NetworkCapabilities.NET_CAPABILITY_INTERNET,
              NetworkCapabilities.NET_CAPABILITY_VALIDATED
            }
            : new int[] {NetworkCapabilities.NET_CAPABILITY_INTERNET};

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

  /**
   * The connection status of a snapshot, or of the legacy {@link ConnectivityManager} query when
   * the snapshot has no capabilities.
   */
  private @NotNull ConnectionStatus statusOf(final @NotNull ConnectionSnapshot snapshot) {
    final @Nullable NetworkCapabilities capabilities = snapshot.capabilities;
    if (capabilities != null) {
      return isNetworkEffectivelyConnected(capabilities)
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

  /**
   * The connection type of a snapshot, or of the legacy {@link ConnectivityManager} query when the
   * snapshot has no capabilities.
   */
  private @Nullable String typeOf(final @NotNull ConnectionSnapshot snapshot) {
    final @Nullable NetworkCapabilities capabilities = snapshot.capabilities;
    if (capabilities != null) {
      return getConnectionType(capabilities);
    }

    // Fallback to legacy method when NetworkCapabilities not available
    return getConnectionType(context, options.getLogger(), buildInfoProvider);
  }

  /**
   * The connection type and, for cellular connections, the generation of the network technology.
   *
   * <p>The type is derived from a single snapshot, so it can't mix two networks across a
   * connectivity change.
   */
  public @NotNull NetworkConnection getConnection() {
    final @Nullable String connectionType = typeOf(freshSnapshot());
    if (!"cellular".equals(connectionType)) {
      return new NetworkConnection(connectionType, null);
    }
    return new NetworkConnection(
        connectionType, cellularNetworkTechnologyProvider.getCellularNetworkTechnology());
  }

  private void ensureNetworkCallbackRegistered() {
    if (closed) {
      return;
    }

    if (!ContextUtils.isForegroundImportance()) {
      return;
    }

    if (networkCallback != null) {
      return; // Already registered
    }

    final boolean registered;
    try (final @NotNull ISentryLifecycleToken ignored = registrationLock.acquire()) {
      // Checked again under the lock in case close() ran in the meantime: its unregistration takes
      // this lock too, so either it sees the registration and undoes it, or this sees the flag.
      if (closed || networkCallback != null) {
        return;
      }

      final @NotNull NetworkCallback callback =
          new NetworkCallback() {
            @Override
            public void onAvailable(final @NotNull Network network) {
              @NotNull ConnectionSnapshot current;
              do {
                current = snapshot.get();
              } while (!snapshot.compareAndSet(current, current.withNetwork(network)));

              // Only forward the call on first registration + when the connection is
              // re-established – not on every foreground.
              if (!onAvailableForwarded.getAndSet(true)) {
                for (final @NotNull NetworkCallback cb : childCallbacks) {
                  cb.onAvailable(network);
                }
              }
            }

            @RequiresApi(Build.VERSION_CODES.O)
            @Override
            public void onUnavailable() {
              clearCacheAndNotifyObservers();

              for (final @NotNull NetworkCallback cb : childCallbacks) {
                cb.onUnavailable();
              }
            }

            @Override
            public void onLost(final @NotNull Network network) {
              if (!network.equals(snapshot.get().network)) {
                return;
              }
              clearCacheAndNotifyObservers();

              for (final @NotNull NetworkCallback cb : childCallbacks) {
                cb.onLost(network);
              }
            }

            private void clearCacheAndNotifyObservers() {
              onAvailableForwarded.set(false);
              snapshot.set(ConnectionSnapshot.lost(ticker));

              options
                  .getLogger()
                  .log(SentryLevel.DEBUG, "Cache cleared - network lost/unavailable");

              // Notify all observers with DISCONNECTED status directly
              // No need to query ConnectivityManager - we know the network is gone
              for (final @NotNull IConnectionStatusObserver observer : connectionStatusObservers) {
                observer.onConnectionStatusChanged(ConnectionStatus.DISCONNECTED);
              }
            }

            @Override
            public void onCapabilitiesChanged(
                @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
              if (!network.equals(snapshot.get().network)) {
                return;
              }
              updateCacheAndNotifyObservers(networkCapabilities);

              for (final @NotNull NetworkCallback cb : childCallbacks) {
                cb.onCapabilitiesChanged(network, networkCapabilities);
              }
            }

            /**
             * Stores the capabilities and notifies observers, but only if they differ from the
             * stored ones in a way that changes the connection status or type.
             */
            private void updateCacheAndNotifyObservers(
                final @NotNull NetworkCapabilities networkCapabilities) {
              @NotNull ConnectionSnapshot current;
              @NotNull ConnectionSnapshot updated;
              do {
                current = snapshot.get();
                if (!isSignificantChange(current.capabilities, networkCapabilities)) {
                  return;
                }
                updated =
                    current.withCapabilities(
                        networkCapabilities,
                        Deadline.after(ticker, CACHE_TTL_MINUTES, TimeUnit.MINUTES));
              } while (!snapshot.compareAndSet(current, updated));

              final @NotNull ConnectionStatus status = statusOf(updated);
              for (final @NotNull IConnectionStatusObserver observer : connectionStatusObservers) {
                observer.onConnectionStatusChanged(status);
              }
            }

            /**
             * Check if NetworkCapabilities change is significant for our observers. Only notify for
             * changes that affect connectivity status or connection type.
             */
            private boolean isSignificantChange(
                final @Nullable NetworkCapabilities oldCapabilities,
                final @NotNull NetworkCapabilities newCapabilities) {
              // Always significant when capabilities first become available
              if (oldCapabilities == null) {
                return true;
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
              for (int capabilityId : capabilityIds) {
                if (old.hasCapability(capabilityId) != new_.hasCapability(capabilityId)) {
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

      registered =
          registerNetworkCallback(
              context, options.getLogger(), buildInfoProvider, handler, callback);
      if (registered) {
        networkCallback = callback;
        // Only start listening once the network callback is registered, because unregistering is
        // skipped while there is no network callback, which would leave the listener running.
        cellularNetworkTechnologyProvider.register();
      }
    }
    if (registered) {
      options.getLogger().log(SentryLevel.DEBUG, "Network callback registered successfully");
    } else {
      options.getLogger().log(SentryLevel.WARNING, "Failed to register network callback");
    }
  }

  /** The current snapshot, refreshed first if it is stale. */
  private @NotNull ConnectionSnapshot freshSnapshot() {
    final @NotNull ConnectionSnapshot current = snapshot.get();
    return current.isFresh() ? current : refresh(current);
  }

  /**
   * Queries the active network and stores its capabilities, unless the snapshot changed during the
   * query.
   *
   * @param before the snapshot the refresh replaces
   * @return the stored snapshot, or, if another thread such as a network callback replaced {@code
   *     before} during the query, that newer snapshot, which may still be stale
   */
  private @NotNull ConnectionSnapshot refresh(final @NotNull ConnectionSnapshot before) {
    final @NotNull ConnectionSnapshot refreshed =
        before.withCapabilities(
            queryActiveNetworkCapabilities(),
            Deadline.after(ticker, CACHE_TTL_MINUTES, TimeUnit.MINUTES));
    if (!snapshot.compareAndSet(before, refreshed)) {
      return snapshot.get();
    }

    // Without capabilities, statusOf and typeOf query the ConnectivityManager, so we only build the
    // message if it'll be logged.
    if (options.getLogger().isEnabled(SentryLevel.DEBUG)) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Cache updated - Status: " + statusOf(refreshed) + ", Type: " + typeOf(refreshed));
    }
    return refreshed;
  }

  /**
   * Queries the capabilities of the active network from the {@link ConnectivityManager}.
   *
   * @return the capabilities, or {@code null} if they can't be read: without permission, below API
   *     23, without an active network, or when the query fails
   */
  @SuppressLint({"NewApi", "MissingPermission"})
  private @Nullable NetworkCapabilities queryActiveNetworkCapabilities() {
    try {
      if (!Permissions.hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)) {
        options
            .getLogger()
            .log(SentryLevel.INFO, "No permission (ACCESS_NETWORK_STATE) to check network status.");
        return null;
      }

      if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.M) {
        return null;
      }

      final ConnectivityManager connectivityManager =
          getConnectivityManager(context, options.getLogger());
      if (connectivityManager == null) {
        return null;
      }
      final Network activeNetwork = connectivityManager.getActiveNetwork();
      return activeNetwork != null
          ? connectivityManager.getNetworkCapabilities(activeNetwork)
          : null;
    } catch (Throwable t) {
      options.getLogger().log(SentryLevel.WARNING, "Failed to update connection status cache", t);
      return null;
    }
  }

  @Override
  public @NotNull ConnectionStatus getConnectionStatus() {
    return statusOf(freshSnapshot());
  }

  @Override
  public @Nullable String getConnectionType() {
    return typeOf(freshSnapshot());
  }

  /**
   * Adds an observer and makes sure the network callback is registered.
   *
   * <p>The observer is added immediately. Registering the network callback calls into system
   * services that can be slow, so it is scheduled on the SDK executor instead of making the caller,
   * often the main thread during {@code Sentry.init}, wait for it.
   *
   * @return always {@code true}, because the observer is added even if registering the network
   *     callback is still pending or fails
   */
  @Override
  public boolean addConnectionStatusObserver(final @NotNull IConnectionStatusObserver observer) {
    connectionStatusObservers.add(observer);
    // The executor drops tasks when its queue is full, which would skip this registration. That is
    // unlikely, and onForeground() registers the callback again the next time the app is shown.
    //noinspection Convert2MethodRef
    submitSafe(() -> ensureNetworkCallbackRegistered());
    return true;
  }

  @Override
  public void removeConnectionStatusObserver(final @NotNull IConnectionStatusObserver observer) {
    connectionStatusObservers.remove(observer);
    // Keep the callback registered for caching even if no observers
  }

  private void unregisterNetworkCallback(final boolean clearObservers) {
    if (clearObservers) {
      connectionStatusObservers.clear();
    }

    try (final @NotNull ISentryLifecycleToken ignored = registrationLock.acquire()) {
      final @Nullable NetworkCallback callbackRef = networkCallback;
      networkCallback = null;

      if (callbackRef != null) {
        unregisterNetworkCallback(context, options.getLogger(), callbackRef);
      }
      cellularNetworkTechnologyProvider.unregister();
      snapshot.set(ConnectionSnapshot.unknown(ticker));
    }
    options.getLogger().log(SentryLevel.DEBUG, "Network callback unregistered");
  }

  /** Clean up resources - should be called when the provider is no longer needed */
  @Override
  public void close() {
    closed = true;
    submitSafe(
        () -> {
          unregisterNetworkCallback(/* clearObservers= */ true);
          childCallbacks.clear();
          try (final @NotNull ISentryLifecycleToken ignored = connectivityManagerLock.acquire()) {
            connectivityManager = null;
          }
          AppState.getInstance().removeAppStateListener(this);
        });
  }

  /**
   * Refreshes the cache, notifies observers and registers the network callback, unless it is
   * already registered or the provider is closed.
   *
   * <p>Whether the callback is registered is checked on the SDK executor, which also runs
   * registration and unregistration in submission order. Checking on the calling thread instead
   * would skip this work when the app returns to the foreground before the unregistration queued by
   * {@link #onBackground()} has run, leaving the callback unregistered while in the foreground.
   */
  @Override
  public void onForeground() {
    submitSafe(
        () -> {
          if (closed || networkCallback != null) {
            return;
          }

          // proactively update cache and notify observers on foreground to ensure connectivity
          // state is not stale
          final @NotNull ConnectionStatus status = statusOf(refresh(snapshot.get()));
          if (status == ConnectionStatus.DISCONNECTED) {
            // onLost is not called retroactively when we registerNetworkCallback (as opposed to
            // onAvailable), so we have to do it manually for the DISCONNECTED case
            onAvailableForwarded.set(false);
            for (final @NotNull NetworkCallback cb : childCallbacks) {
              //noinspection DataFlowIssue
              cb.onLost(null);
            }
          }
          for (final @NotNull IConnectionStatusObserver observer : connectionStatusObservers) {
            observer.onConnectionStatusChanged(status);
          }

          // this will ONLY do the necessary parts like registerNetworkCallback and onAvailable, but
          // it won't refresh the cache and notify observers because we just did it above and the
          // cached
          // capabilities will be the same
          ensureNetworkCallbackRegistered();
        });
  }

  /**
   * Unregisters the network callback and the cellular technology listener, if the network callback
   * is registered.
   *
   * <p>Whether the callback is registered is checked on the SDK executor, which also runs
   * registration and unregistration in submission order. Checking on the calling thread instead
   * would skip unregistering when the app goes to the background while a registration is queued or
   * running, leaving the callbacks registered in the background.
   */
  @Override
  public void onBackground() {
    submitSafe(
        () -> {
          if (networkCallback == null) {
            return;
          }
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
    return snapshot.get().capabilities;
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

    childCallbacks.add(networkCallback);
    return true;
  }

  public static void removeNetworkCallback(final @NotNull NetworkCallback networkCallback) {
    childCallbacks.remove(networkCallback);
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

  /**
   * An immutable view of the default network: which network it is, its capabilities, and until when
   * they can be used without querying the {@link ConnectivityManager} again.
   *
   * <p>Each change replaces the whole snapshot, so a reader that reads it once gets values that
   * belong together, and a refresh can detect with a compare-and-set that a network callback
   * replaced it during the query.
   */
  private static final class ConnectionSnapshot {
    /** The default network reported by the network callback, or {@code null} if none is known. */
    final @Nullable Network network;

    /**
     * The capabilities of the default network, or {@code null} if they are unknown or there is no
     * default network. Readers then fall back to the legacy {@link ConnectivityManager} query.
     */
    final @Nullable NetworkCapabilities capabilities;

    final @NotNull Deadline freshUntil;

    private ConnectionSnapshot(
        final @Nullable Network network,
        final @Nullable NetworkCapabilities capabilities,
        final @NotNull Deadline freshUntil) {
      this.network = network;
      this.capabilities = capabilities;
      this.freshUntil = freshUntil;
    }

    /** Nothing is known, so the next read queries the active network. */
    static @NotNull ConnectionSnapshot unknown(final @NotNull MonotonicTicker ticker) {
      return new ConnectionSnapshot(null, null, Deadline.passed(ticker));
    }

    /**
     * The network callback reported that the default network is gone. The snapshot is not refreshed
     * until the cache time-to-live has elapsed, but it has no capabilities, so reading the status
     * or type still falls back to the legacy {@link ConnectivityManager} query on every read.
     */
    static @NotNull ConnectionSnapshot lost(final @NotNull MonotonicTicker ticker) {
      return new ConnectionSnapshot(
          null, null, Deadline.after(ticker, CACHE_TTL_MINUTES, TimeUnit.MINUTES));
    }

    boolean isFresh() {
      return !freshUntil.hasPassed();
    }

    @NotNull
    ConnectionSnapshot withNetwork(final @NotNull Network network) {
      return new ConnectionSnapshot(network, capabilities, freshUntil);
    }

    @NotNull
    ConnectionSnapshot withCapabilities(
        final @Nullable NetworkCapabilities capabilities, final @NotNull Deadline freshUntil) {
      return new ConnectionSnapshot(network, capabilities, freshUntil);
    }
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
