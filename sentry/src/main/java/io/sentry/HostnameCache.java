package io.sentry;

import io.sentry.time.Deadline;
import io.sentry.time.JavaMonotonicTicker;
import io.sentry.time.MonotonicTicker;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.net.InetAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
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

  /** How long the worker thread may stay idle before it self-terminates. */
  private static final long THREAD_KEEP_ALIVE_SECONDS = 30;

  private static volatile @Nullable HostnameCache INSTANCE;
  private static final @NotNull AutoClosableReentrantLock staticLock =
      new AutoClosableReentrantLock();

  /** Time for which the cache is kept, in milliseconds. */
  private final long cacheDurationMillis;

  private final @NotNull MonotonicTicker ticker;

  /** Current value for hostname (might change over time). */
  @Nullable private volatile String hostname;

  /** When the cached hostname goes stale. */
  private volatile @NotNull Deadline cacheFreshUntil;

  /** Whether a cache update thread is currently running or not. */
  private final @NotNull AtomicBoolean updateRunning = new AtomicBoolean(false);

  private final @NotNull Callable<InetAddress> getLocalhost;

  private final @NotNull ExecutorService executorService;

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

  HostnameCache(long cacheDurationMillis) {
    // avoid method refs on Android due to some issues with older AGP setups
    // noinspection Convert2MethodRef
    this(cacheDurationMillis, () -> InetAddress.getLocalHost());
  }

  HostnameCache(long cacheDurationMillis, final @NotNull Callable<InetAddress> getLocalhost) {
    this(cacheDurationMillis, getLocalhost, JavaMonotonicTicker.getInstance());
  }

  /**
   * Sets up a cache for the hostname.
   *
   * @param cacheDurationMillis cache duration in milliseconds.
   * @param getLocalhost a callback to obtain the localhost address - this is mostly here because of
   *     testability
   * @param ticker the ticker the cache lifetime is measured on
   */
  HostnameCache(
      long cacheDurationMillis,
      final @NotNull Callable<InetAddress> getLocalhost,
      final @NotNull MonotonicTicker ticker) {
    this.cacheDurationMillis = cacheDurationMillis;
    this.getLocalhost = Objects.requireNonNull(getLocalhost, "getLocalhost is required");
    this.ticker = Objects.requireNonNull(ticker, "ticker is required");
    // Nothing resolved yet, so the cache is stale rather than fresh until updateCache says
    // otherwise.
    this.cacheFreshUntil = Deadline.passed(ticker);
    // A single thread executor whose worker thread times out while idle, so no thread is kept
    // alive between the infrequent cache refreshes.
    final @NotNull ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            1,
            1,
            THREAD_KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new HostnameCacheThreadFactory());
    executor.allowCoreThreadTimeOut(true);
    this.executorService = executor;
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
    if (cacheFreshUntil.hasPassed() && updateRunning.compareAndSet(false, true)) {
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
            cacheFreshUntil = Deadline.after(ticker, cacheDurationMillis, TimeUnit.MILLISECONDS);
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
    cacheFreshUntil = Deadline.after(ticker, 1, TimeUnit.SECONDS);
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
