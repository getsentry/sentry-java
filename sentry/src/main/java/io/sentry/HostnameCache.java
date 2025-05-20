package io.sentry;

import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.net.InetAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Time sensitive cache in charge of keeping track of the hostname. The {@code
 * InetAddress.getLocalHost().getCanonicalHostName()} call can be quite expensive and could be
 * called for the creation of each {@link SentryEvent}. This system will prevent unnecessary costs
 * by keeping track of the hostname for a period defined during the construction. For performance
 * purposes, the operation of retrieving the hostname will automatically fail after a period of time
 * defined by {@link #GET_HOSTNAME_TIMEOUT} without result.
 *
 * <p>HostnameCache is a singleton and its instance should be obtained through {@link
 * HostnameCache#getInstance()}.
 */
@ApiStatus.Internal
public final class HostnameCache {
  private static final long HOSTNAME_CACHE_DURATION = TimeUnit.HOURS.toMillis(5);

  /** Time before the get hostname operation times out (in ms). */
  private static final long GET_HOSTNAME_TIMEOUT = TimeUnit.SECONDS.toMillis(1);

  private static volatile @Nullable HostnameCache INSTANCE;
  private static final @NotNull AutoClosableReentrantLock staticLock =
      new AutoClosableReentrantLock();

  /** Time for which the cache is kept. */
  private final long cacheDuration;
  /** Current value for hostname (might change over time). */
  @Nullable private volatile String hostname;
  /** Time at which the cache should expire. */
  private volatile long expirationTimestamp;
  /** Whether a cache update thread is currently running or not. */
  private final @NotNull AtomicBoolean updateRunning = new AtomicBoolean(false);

  private final @NotNull Callable<InetAddress> getLocalhost;

  private final @NotNull ExecutorService executorService =
      Executors.newSingleThreadExecutor(new HostnameCacheThreadFactory());

  public static @NotNull HostnameCache getInstance() {
    if (INSTANCE == null) {
      try (final @NotNull ISentryLifecycleToken ignored = staticLock.acquire()) {
        if (INSTANCE == null) {
          INSTANCE = new HostnameCache();
        }
      }
    }

    return INSTANCE;
  }

  private HostnameCache() {
    this(HOSTNAME_CACHE_DURATION);
  }

  HostnameCache(long cacheDuration) {
    // avoid method refs on Android due to some issues with older AGP setups
    // noinspection Convert2MethodRef
    this(cacheDuration, () -> InetAddress.getLocalHost());
  }

  /**
   * Sets up a cache for the hostname.
   *
   * @param cacheDuration cache duration in milliseconds.
   * @param getLocalhost a callback to obtain the localhost address - this is mostly here because of
   *     testability
   */
  HostnameCache(long cacheDuration, final @NotNull Callable<InetAddress> getLocalhost) {
    this.cacheDuration = cacheDuration;
    this.getLocalhost = Objects.requireNonNull(getLocalhost, "getLocalhost is required");
    updateCache();
  }

  void close() {
    this.executorService.shutdown();
  }

  boolean isClosed() {
    return this.executorService.isShutdown();
  }

  /**
   * Gets the hostname of the current machine.
   *
   * <p>Gets the value from the cache if possible otherwise calls {@link #updateCache()}.
   *
   * @return the hostname of the current machine.
   */
  @Nullable
  public String getHostname() {
    if (expirationTimestamp < System.currentTimeMillis()
        && updateRunning.compareAndSet(false, true)) {
      updateCache();
    }

    return hostname;
  }

  /** Force an update of the cache to get the current value of the hostname. */
  private void updateCache() {
    final Callable<Void> hostRetriever =
        () -> {
          try {
            hostname = getLocalhost.call().getCanonicalHostName();
            expirationTimestamp = System.currentTimeMillis() + cacheDuration;
          } finally {
            updateRunning.set(false);
          }

          return null;
        };

    try {
      final Future<Void> futureTask = executorService.submit(hostRetriever);
      futureTask.get(GET_HOSTNAME_TIMEOUT, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      handleCacheUpdateFailure();
    } catch (ExecutionException | TimeoutException | RuntimeException e) {
      handleCacheUpdateFailure();
    }
  }

  private void handleCacheUpdateFailure() {
    expirationTimestamp = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(1);
  }

  private static final class HostnameCacheThreadFactory implements ThreadFactory {
    private int cnt;

    @Override
    public @NotNull Thread newThread(final @NotNull Runnable r) {
      final Thread ret = new Thread(r, "SentryHostnameCache-" + cnt++);
      ret.setDaemon(true);
      return ret;
    }
  }
}
