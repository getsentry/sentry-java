package io.sentry;

import static org.asynchttpclient.Dsl.asyncHttpClient;

import io.sentry.transport.NIOConnection;
import io.sentry.transport.RateLimiter;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.jetbrains.annotations.NotNull;

public final class NIOConnectionFactory {
  private NIOConnectionFactory() {}

  public static NIOConnection create(final @NotNull SentryOptions options) {
    final AsyncHttpClient asyncHttpClient =
        asyncHttpClient(new DefaultAsyncHttpClientConfig.Builder().setKeepAlive(true).build());
    final ExecutorService executorService = Executors.newFixedThreadPool(2);
    final RateLimiter rateLimiter = new RateLimiter(options.getLogger());

    final Dsn dsn = new Dsn(options.getDsn());
    final URI sentryUri = dsn.getSentryUri();
    final String envelopeUrl = sentryUri.resolve(sentryUri.getPath() + "/envelope/").toString();

    final CredentialsSettingConfigurator credentials =
        new CredentialsSettingConfigurator(dsn, options.getSentryClientName());

    return new NIOConnection(
        options.getLogger(),
        options.getSerializer(),
        asyncHttpClient,
        executorService,
        rateLimiter,
        envelopeUrl,
        credentials.getAuthHeader());
  }
}
