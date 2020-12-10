package io.sentry.transport;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;

import io.sentry.ILogger;
import io.sentry.ISerializer;
import io.sentry.SentryEnvelope;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.zip.GZIPOutputStream;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A connection to Sentry that sends the events using {@link AsyncHttpClient} in a non blocking
 * manner.
 *
 * <p>TODO: HTTP proxy TODO: hostnameVerifier TODO: sslSocketFactory TODO: add client header to the
 * request
 */
@ApiStatus.Internal
public final class NIOConnection implements Closeable, Connection {
  private final @NotNull ILogger logger;
  private final @NotNull ISerializer serializer;
  private final @NotNull String envelopeUrl;
  private final @NotNull String authHeader;
  private final @NotNull AsyncHttpClient asyncHttpClient;
  private final @NotNull ExecutorService executorService;
  private final @NotNull RateLimiter rateLimiter;

  public NIOConnection(
      final @NotNull ILogger logger,
      final @NotNull ISerializer serializer,
      final @NotNull AsyncHttpClient asyncHttpClient,
      final @NotNull ExecutorService executorService,
      final @NotNull RateLimiter rateLimiter,
      final @NotNull String envelopeUrl,
      final @NotNull String authHeader) {
    this.logger = logger;
    this.serializer = serializer;
    this.envelopeUrl = envelopeUrl;
    this.authHeader = authHeader;
    this.asyncHttpClient = asyncHttpClient;
    this.executorService = executorService;
    this.rateLimiter = rateLimiter;
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void send(final @NotNull SentryEnvelope envelope, final @Nullable Object hint) {
    final SentryEnvelope filteredEnvelope = rateLimiter.filter(envelope, hint);

    if (filteredEnvelope != null) {
      try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          final GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
        serializer.serialize(filteredEnvelope, gzip);

        final ListenableFuture<Response> execute =
            asyncHttpClient
                .preparePost(envelopeUrl)
                .addHeader("Content-Encoding", "gzip")
                .addHeader("Content-Type", "application/x-sentry-envelope")
                .addHeader("Accept", "application/json")
                .addHeader("X-Sentry-Auth", authHeader)
                .setBody(outputStream.toByteArray())
                .execute();

        execute.addListener(
            () -> {
              try {
                handleResponse(execute.get());
              } catch (InterruptedException | ExecutionException e) {
                logger.log(ERROR, "Error while executing an HTTP request", e);
              }
            },
            executorService);
      } catch (Exception e) {
        logger.log(ERROR, "Error while sending an envelope", e);
      }
    }
  }

  private void handleResponse(final @NotNull Response response) {
    if (response.getStatusCode() != 200) {
      if (logger.isEnabled(ERROR)) {
        logger.log(
            ERROR,
            "Request failed, API returned %s with body %s",
            response.getStatusCode(),
            response.getResponseBody());
      }
    } else {
      logger.log(DEBUG, "Envelope sent successfully.");
    }
    rateLimiter.updateRetryAfterLimits(
        response.getHeader("Retry-After"),
        response.getHeader("X-Sentry-Rate-Limits"),
        response.getStatusCode());
  }

  @Override
  public void close() throws IOException {
    executorService.shutdown();
    asyncHttpClient.close();
  }
}
