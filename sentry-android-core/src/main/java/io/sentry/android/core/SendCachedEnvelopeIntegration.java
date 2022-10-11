package io.sentry.android.core;

import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SendCachedEnvelopeFireAndForgetIntegration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.cache.AndroidEnvelopeCache;
import io.sentry.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;

final class SendCachedEnvelopeIntegration implements Integration {

  private final @NotNull SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetFactory
      factory;

  public SendCachedEnvelopeIntegration(
      final @NotNull SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetFactory factory) {
    this.factory = Objects.requireNonNull(factory, "SendFireAndForgetFactory is required");
  }

  @Override
  public void register(@NotNull IHub hub, @NotNull SentryOptions options) {
    Objects.requireNonNull(hub, "Hub is required");
    final SentryAndroidOptions androidOptions =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    final SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForget sender =
        factory.create(hub, androidOptions);

    if (sender == null) {
      androidOptions.getLogger().log(SentryLevel.ERROR, "SendFireAndForget factory is null.");
      return;
    }

    try {
      Future<?> future =
          androidOptions
              .getExecutorService()
              .submit(
                  () -> {
                    try {
                      sender.send();
                    } catch (Throwable e) {
                      androidOptions
                          .getLogger()
                          .log(SentryLevel.ERROR, "Failed trying to send cached events.", e);
                    }
                  });

      final String dirPath = factory.getDirPath();
      if (dirPath != null && AndroidEnvelopeCache.hasStartupCrashMarker(dirPath, androidOptions)) {
        androidOptions
            .getLogger()
            .log(SentryLevel.DEBUG, "Startup Crash marker exists, blocking flush.");
        try {
          future.get(androidOptions.getStartupCrashFlushTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
          androidOptions
              .getLogger()
              .log(SentryLevel.DEBUG, "Synchronous send timed out, continuing in the background.");
        }
      } else {
        androidOptions
            .getLogger()
            .log(SentryLevel.DEBUG, "No Startup Crash marker exists, flushing asynchronously.");
      }

      androidOptions
          .getLogger()
          .log(SentryLevel.DEBUG, "SendCachedEventFireAndForgetIntegration installed.");
    } catch (Throwable e) {
      androidOptions
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to call the executor. Cached events will not be sent", e);
    }
  }
}
