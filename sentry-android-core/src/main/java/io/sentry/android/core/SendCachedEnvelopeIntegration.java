package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.sentry.DataCategory;
import io.sentry.IConnectionStatusProvider;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.Integration;
import io.sentry.SendCachedEnvelopeFireAndForgetIntegration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.transport.RateLimiter;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.LazyEvaluator;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SendCachedEnvelopeIntegration
    implements Integration, IConnectionStatusProvider.IConnectionStatusObserver, Closeable {

  private final @NotNull SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetFactory
      factory;
  private final @NotNull LazyEvaluator<Boolean> startupCrashMarkerEvaluator;
  private final AtomicBoolean startupCrashHandled = new AtomicBoolean(false);
  private @Nullable IConnectionStatusProvider connectionStatusProvider;
  private @Nullable IScopes scopes;
  private @Nullable SentryAndroidOptions options;
  private @Nullable SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForget sender;
  private final AtomicBoolean isInitialized = new AtomicBoolean(false);
  private final AtomicBoolean isClosed = new AtomicBoolean(false);
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  public SendCachedEnvelopeIntegration(
      final @NotNull SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetFactory factory,
      final @NotNull LazyEvaluator<Boolean> startupCrashMarkerEvaluator) {
    this.factory = Objects.requireNonNull(factory, "SendFireAndForgetFactory is required");
    this.startupCrashMarkerEvaluator = startupCrashMarkerEvaluator;
  }

  @Override
  public void register(@NotNull IScopes scopes, @NotNull SentryOptions options) {
    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    final String cachedDir = options.getCacheDirPath();
    if (!factory.hasValidPath(cachedDir, options.getLogger())) {
      options.getLogger().log(SentryLevel.ERROR, "No cache dir path is defined in options.");
      return;
    }
    addIntegrationToSdkVersion("SendCachedEnvelope");

    sendCachedEnvelopes(scopes, this.options);
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
    if (scopes != null && options != null) {
      sendCachedEnvelopes(scopes, options);
    }
  }

  @SuppressWarnings({"NullAway"})
  private void sendCachedEnvelopes(
      final @NotNull IScopes scopes, final @NotNull SentryAndroidOptions options) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final Future<?> future =
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
                                "SendCachedEnvelopeIntegration, not trying to send after closing.");
                        return;
                      }

                      if (!isInitialized.getAndSet(true)) {
                        connectionStatusProvider = options.getConnectionStatusProvider();
                        connectionStatusProvider.addConnectionStatusObserver(this);

                        sender = factory.create(scopes, options);
                      }

                      if (connectionStatusProvider != null
                          && connectionStatusProvider.getConnectionStatus()
                              == IConnectionStatusProvider.ConnectionStatus.DISCONNECTED) {
                        options
                            .getLogger()
                            .log(SentryLevel.INFO, "SendCachedEnvelopeIntegration, no connection.");
                        return;
                      }

                      // in case there's rate limiting active, skip processing
                      final @Nullable RateLimiter rateLimiter = scopes.getRateLimiter();
                      if (rateLimiter != null
                          && rateLimiter.isActiveForCategory(DataCategory.All)) {
                        options
                            .getLogger()
                            .log(
                                SentryLevel.INFO,
                                "SendCachedEnvelopeIntegration, rate limiting active.");
                        return;
                      }

                      if (sender == null) {
                        options
                            .getLogger()
                            .log(
                                SentryLevel.ERROR,
                                "SendCachedEnvelopeIntegration factory is null.");
                        return;
                      }

                      sender.send();
                    } catch (Throwable e) {
                      options
                          .getLogger()
                          .log(SentryLevel.ERROR, "Failed trying to send cached events.", e);
                    }
                  });

      // startupCrashMarkerEvaluator remains true on subsequent runs, let's ensure we only block on
      // the very first execution (=app start)
      if (startupCrashMarkerEvaluator.getValue()
          && startupCrashHandled.compareAndSet(false, true)) {
        options.getLogger().log(SentryLevel.DEBUG, "Startup Crash marker exists, blocking flush.");
        try {
          future.get(options.getStartupCrashFlushTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
          options
              .getLogger()
              .log(SentryLevel.DEBUG, "Synchronous send timed out, continuing in the background.");
        }
      }
      options.getLogger().log(SentryLevel.DEBUG, "SendCachedEnvelopeIntegration installed.");
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
