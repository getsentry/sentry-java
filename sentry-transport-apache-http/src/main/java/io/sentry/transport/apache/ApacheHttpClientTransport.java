package io.sentry.transport.apache;

import static io.sentry.SentryLevel.*;

import io.sentry.RequestDetails;
import io.sentry.SentryEnvelope;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.transport.ITransport;
import io.sentry.transport.RateLimiter;
import io.sentry.util.Objects;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ITransport} implementation that executes request asynchronously in a non-blocking manner
 * using Apache Http Client 5.
 */
public final class ApacheHttpClientTransport implements ITransport {
  private final @NotNull SentryOptions options;
  private final @NotNull RequestDetails requestDetails;
  private final @NotNull CloseableHttpAsyncClient httpclient;
  private final @NotNull RateLimiter rateLimiter;
  private final @NotNull AtomicInteger currentlyRunning;

  public ApacheHttpClientTransport(
      final @NotNull SentryOptions options,
      final @NotNull RequestDetails requestDetails,
      final @NotNull CloseableHttpAsyncClient httpclient,
      final @NotNull RateLimiter rateLimiter) {
    this(options, requestDetails, httpclient, rateLimiter, new AtomicInteger());
  }

  ApacheHttpClientTransport(
      final @NotNull SentryOptions options,
      final @NotNull RequestDetails requestDetails,
      final @NotNull CloseableHttpAsyncClient httpclient,
      final @NotNull RateLimiter rateLimiter,
      final @NotNull AtomicInteger currentlyRunning) {
    this.options = Objects.requireNonNull(options, "options is required");
    this.requestDetails = Objects.requireNonNull(requestDetails, "requestDetails is required");
    this.httpclient = Objects.requireNonNull(httpclient, "httpclient is required");
    this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter is required");
    this.currentlyRunning =
        Objects.requireNonNull(currentlyRunning, "currentlyRunning is required");
    this.httpclient.start();
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void send(SentryEnvelope envelope, Object hint) throws IOException {
    if (isSchedulingAllowed()) {
      final SentryEnvelope filteredEnvelope = rateLimiter.filter(envelope, hint);

      if (filteredEnvelope != null) {
        currentlyRunning.incrementAndGet();

        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
          options.getSerializer().serialize(filteredEnvelope, gzip);

          final SimpleHttpRequest request =
              SimpleHttpRequests.post(requestDetails.getUrl().toString());
          request.setBody(outputStream.toByteArray(), ContentType.APPLICATION_JSON);
          request.setHeader("Content-Encoding", "gzip");
          request.setHeader("Content-Type", "application/x-sentry-envelope");
          request.setHeader("Accept", "application/json");

          for (Map.Entry<String, String> header : requestDetails.getHeaders().entrySet()) {
            request.setHeader(header.getKey(), header.getValue());
          }

          if (options.getLogger().isEnabled(DEBUG)) {
            options.getLogger().log(DEBUG, "Currently running %d requests", currentlyRunning.get());
          }

          httpclient.execute(
              request,
              new FutureCallback<SimpleHttpResponse>() {
                @Override
                public void completed(SimpleHttpResponse response) {
                  currentlyRunning.decrementAndGet();
                  if (response.getCode() != 200) {
                    options
                        .getLogger()
                        .log(ERROR, "Request failed, API returned %s", response.getCode());
                  } else {
                    options.getLogger().log(INFO, "Envelope sent successfully.");
                  }
                  final Header retryAfter = response.getFirstHeader("Retry-After");
                  final Header rateLimits = response.getFirstHeader("X-Sentry-Rate-Limits");
                  rateLimiter.updateRetryAfterLimits(
                      rateLimits != null ? rateLimits.getValue() : null,
                      retryAfter != null ? retryAfter.getValue() : null,
                      response.getCode());
                }

                @Override
                public void failed(Exception ex) {
                  currentlyRunning.decrementAndGet();
                  options.getLogger().log(ERROR, "Error while sending an envelope", ex);
                }

                @Override
                public void cancelled() {
                  currentlyRunning.decrementAndGet();
                  options.getLogger().log(WARNING, "Request cancelled");
                }
              });
        } catch (Exception e) {
          options.getLogger().log(ERROR, "Error when sending envelope", e);
        }
      }
    } else {
      options.getLogger().log(SentryLevel.WARNING, "Submit cancelled");
    }
  }

  @Override
  public void close() throws IOException {
    options.getLogger().log(DEBUG, "Shutting down");
    try {
      httpclient.awaitShutdown(TimeValue.ofSeconds(1));
      httpclient.close(CloseMode.GRACEFUL);
    } catch (InterruptedException e) {
      options.getLogger().log(DEBUG, "Thread interrupted while closing the connection.");
      Thread.currentThread().interrupt();
    }
  }

  private boolean isSchedulingAllowed() {
    return currentlyRunning.get() < options.getMaxQueueSize();
  }
}
