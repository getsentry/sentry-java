package io.sentry;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.sentry.transport.RateLimiter;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sends cached events over when your App is starting or a network connection is present. */
public final class SendCachedEnvelopeFireAndForgetIntegration
    implements Integration, IConnectionStatusProvider.IConnectionStatusObserver, Closeable {

  private final @NotNull SendFireAndForgetFactory factory;
  private @Nullable IConnectionStatusProvider connectionStatusProvider;
  private @Nullable IHub hub;
  private @Nullable SentryOptions options;
  private @Nullable SendFireAndForget sender;
  private final AtomicBoolean isInitialized = new AtomicBoolean(false);
  private final AtomicBoolean isClosed = new AtomicBoolean(false);

  public interface SendFireAndForget {
    void send();
  }

  public interface SendFireAndForgetDirPath {
    @Nullable
    String getDirPath();
  }

  public interface SendFireAndForgetFactory {
    @Nullable
    SendFireAndForget create(@NotNull IHub hub, @NotNull SentryOptions options);

    default boolean hasValidPath(final @Nullable String dirPath, final @NotNull ILogger logger) {
      if (dirPath == null || dirPath.isEmpty()) {
        logger.log(SentryLevel.INFO, "No cached dir path is defined in options.");
        return false;
      }
      return true;
    }

    default @NotNull SendFireAndForget processDir(
        final @NotNull DirectoryProcessor directoryProcessor,
        final @NotNull String dirPath,
        final @NotNull ILogger logger) {
      final File dirFile = new File(dirPath);
      return () -> {
        logger.log(SentryLevel.DEBUG, "Started processing cached files from %s", dirPath);

        directoryProcessor.processDirectory(dirFile);

        logger.log(SentryLevel.DEBUG, "Finished processing cached files from %s", dirPath);
      };
    }
  }

  public SendCachedEnvelopeFireAndForgetIntegration(
      final @NotNull SendFireAndForgetFactory factory) {
    this.factory = Objects.requireNonNull(factory, "SendFireAndForgetFactory is required");
  }

  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    this.hub = Objects.requireNonNull(hub, "Hub is required");
    this.options = Objects.requireNonNull(options, "SentryOptions is required");

    final String cachedDir = options.getCacheDirPath();
    if (!factory.hasValidPath(cachedDir, options.getLogger())) {
      options.getLogger().log(SentryLevel.ERROR, "No cache dir path is defined in options.");
      return;
    }

    options
        .getLogger()
        .log(SentryLevel.DEBUG, "SendCachedEventFireAndForgetIntegration installed.");
    addIntegrationToSdkVersion("SendCachedEnvelopeFireAndForget");

    sendCachedEnvelopes(hub, options);
  }

  @Override
  public void close() throws IOException {
    isClosed.set(true);
    if (connectionStatusProvider != null) {
      connectionStatusProvider.removeConnectionStatusObserver(this);
    }
  }

  @Override
  public void onConnectionStatusChanged(
      final @NotNull IConnectionStatusProvider.ConnectionStatus status) {
    if (hub != null && options != null) {
      sendCachedEnvelopes(hub, options);
    }
  }

  @SuppressWarnings({"FutureReturnValueIgnored", "NullAway"})
  private synchronized void sendCachedEnvelopes(
      final @NotNull IHub hub, final @NotNull SentryOptions options) {
    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                try {
                  if (isClosed.get()) {
                    options
                        .getLogger()
                        .log(
                            SentryLevel.INFO,
                            "SendCachedEnvelopeFireAndForgetIntegration, not trying to send after closing.");
                    return;
                  }

                  if (!isInitialized.getAndSet(true)) {
                    connectionStatusProvider = options.getConnectionStatusProvider();
                    connectionStatusProvider.addConnectionStatusObserver(this);

                    sender = factory.create(hub, options);
                  }

                  // skip run only if we're certainly disconnected
                  if (connectionStatusProvider != null
                      && connectionStatusProvider.getConnectionStatus()
                          == IConnectionStatusProvider.ConnectionStatus.DISCONNECTED) {
                    options
                        .getLogger()
                        .log(
                            SentryLevel.INFO,
                            "SendCachedEnvelopeFireAndForgetIntegration, no connection.");
                    return;
                  }

                  // in case there's rate limiting active, skip processing
                  final @Nullable RateLimiter rateLimiter = hub.getRateLimiter();
                  if (rateLimiter != null && rateLimiter.isActiveForCategory(DataCategory.All)) {
                    options
                        .getLogger()
                        .log(
                            SentryLevel.INFO,
                            "SendCachedEnvelopeFireAndForgetIntegration, rate limiting active.");
                    return;
                  }

                  if (sender == null) {
                    options
                        .getLogger()
                        .log(SentryLevel.ERROR, "SendFireAndForget factory is null.");
                    return;
                  }

                  sender.send();
                } catch (Throwable e) {
                  options
                      .getLogger()
                      .log(SentryLevel.ERROR, "Failed trying to send cached events.", e);
                }
              });
    } catch (RejectedExecutionException e) {
      options
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "Failed to call the executor. Cached events will not be sent. Did you call Sentry.close()?",
              e);
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to call the executor. Cached events will not be sent", e);
    }
  }
}
