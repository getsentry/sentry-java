package io.sentry;

import io.sentry.transport.ApacheHttpClientTransport;
import io.sentry.transport.ITransport;
import io.sentry.transport.RateLimiter;
import io.sentry.util.Objects;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.util.TimeValue;
import org.jetbrains.annotations.NotNull;

/** Creates {@link ApacheHttpClientTransport}. */
public final class ApacheHttpClientTransportFactory implements ITransportFactory {
  private final @NotNull TimeValue connectionTimeToLive;

  public ApacheHttpClientTransportFactory() {
    this(TimeValue.ofMinutes(1));
  }

  public ApacheHttpClientTransportFactory(final @NotNull TimeValue connectionTimeToLive) {
    this.connectionTimeToLive =
        Objects.requireNonNull(connectionTimeToLive, "connectionTimeToLive is required");
  }

  @Override
  public ITransport create(
      final @NotNull SentryOptions options, final @NotNull RequestDetails requestDetails) {
    Objects.requireNonNull(options, "options is required");
    Objects.requireNonNull(requestDetails, "requestDetails is required");

    final PoolingAsyncClientConnectionManager connectionManager =
        PoolingAsyncClientConnectionManagerBuilder.create()
            .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.LAX)
            .setConnectionTimeToLive(connectionTimeToLive)
            .setMaxConnTotal(options.getMaxQueueSize())
            .setMaxConnPerRoute(options.getMaxQueueSize())
            .build();

    final CloseableHttpAsyncClient httpclient =
        HttpAsyncClients.custom()
            .setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE)
            .setConnectionManager(connectionManager)
            .setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE)
            .build();

    final RateLimiter rateLimiter = new RateLimiter(options.getLogger());

    return new ApacheHttpClientTransport(options, requestDetails, httpclient, rateLimiter);
  }
}
