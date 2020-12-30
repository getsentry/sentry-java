package io.sentry;

import io.sentry.util.Objects;
import java.net.InetAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An {@link EventProcessor} that sets the local ip address to an {@link
 * SentryEvent#getServerName()} property.
 *
 * <p>Note - this event processor is not used by SDK by default and must be configured manually on
 * {@link SentryOptions} if there is intention to use it.
 */
public final class ServerNameResolvingEventProcessor implements EventProcessor {

  /**
   * Duration of the hostname caching.
   *
   * @see HostnameCache
   */
  private static final long HOSTNAME_CACHE_DURATION = TimeUnit.HOURS.toMillis(5);

  /** The global hostname cache to speed up localhost hostname resolution. */
  private final HostnameCache hostnameCache;

  public ServerNameResolvingEventProcessor() {
    this(new HostnameCache(HOSTNAME_CACHE_DURATION));
  }

  ServerNameResolvingEventProcessor(final @NotNull HostnameCache hostnameCache) {
    this.hostnameCache = hostnameCache;
  }

  @Override
  public @NotNull SentryEvent process(
      final @NotNull SentryEvent event, final @Nullable Object hint) {
    if (event.getServerName() == null) {
      event.setServerName(hostnameCache.getHostname());
    }
    return event;
  }

  /**
   * Time sensitive cache in charge of keeping track of the hostname. The {@code
   * InetAddress.getLocalHost().getCanonicalHostName()} call can be quite expensive and could be
   * called for the creation of each {@link SentryEvent}. This system will prevent unnecessary costs
   * by keeping track of the hostname for a period defined during the construction. For performance
   * purposes, the operation of retrieving the hostname will automatically fail after a period of
   * time defined by {@link #GET_HOSTNAME_TIMEOUT} without result.
   */
  static final class HostnameCache {
    /** Time before the get hostname operation times out (in ms). */
    private static final long GET_HOSTNAME_TIMEOUT = TimeUnit.SECONDS.toMillis(1);
    /** Time for which the cache is kept. */
    private final long cacheDuration;
    /** Current value for hostname (might change over time). */
    @Nullable private volatile String hostname;
    /** Time at which the cache should expire. */
    private volatile long expirationTimestamp;
    /** Whether a cache update thread is currently running or not. */
    private final @NotNull AtomicBoolean updateRunning = new AtomicBoolean(false);

    private final @NotNull Callable<InetAddress> getLocalhost;

    private HostnameCache(long cacheDuration) {
      this(cacheDuration, InetAddress::getLocalHost);
    }

    /**
     * Sets up a cache for the hostname.
     *
     * @param cacheDuration cache duration in milliseconds.
     * @param getLocalhost a callback to obtain the localhost address - this is mostly here because
     *     of testability
     */
    HostnameCache(long cacheDuration, final @NotNull Callable<InetAddress> getLocalhost) {
      this.cacheDuration = cacheDuration;
      this.getLocalhost = Objects.requireNonNull(getLocalhost, "getLocalhost is required");
    }

    /**
     * Gets the hostname of the current machine.
     *
     * <p>Gets the value from the cache if possible otherwise calls {@link #updateCache()}.
     *
     * @return the hostname of the current machine.
     */
    private @Nullable String getHostname() {
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
        final FutureTask<Void> futureTask = new FutureTask<>(hostRetriever);
        new Thread(futureTask).start();
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
  }
}
