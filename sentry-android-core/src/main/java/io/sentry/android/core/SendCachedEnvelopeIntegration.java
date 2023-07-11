package io.sentry.android.core;

import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SendCachedEnvelopeFireAndForgetIntegration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.LazyEvaluator;
import io.sentry.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;

final class SendCachedEnvelopeIntegration implements Integration {

  private final @NotNull SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetFactory
      factory;
  private final @NotNull LazyEvaluator<Boolean> startupCrashMarkerEvaluator;

  public SendCachedEnvelopeIntegration(
      final @NotNull SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetFactory factory,
      final @NotNull LazyEvaluator<Boolean> startupCrashMarkerEvaluator) {
    this.factory = Objects.requireNonNull(factory, "SendFireAndForgetFactory is required");
    this.startupCrashMarkerEvaluator = startupCrashMarkerEvaluator;
  }

  @Override
  public void register(@NotNull IHub hub, @NotNull SentryOptions options) {
    Objects.requireNonNull(hub, "Hub is required");
    final SentryAndroidOptions androidOptions =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    final String cachedDir = options.getCacheDirPath();
    if (!factory.hasValidPath(cachedDir, options.getLogger())) {
      options.getLogger().log(SentryLevel.ERROR, "No cache dir path is defined in options.");
      return;
    }

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

      if (startupCrashMarkerEvaluator.getValue()) {
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
      }
      androidOptions.getLogger().log(SentryLevel.DEBUG, "SendCachedEnvelopeIntegration installed.");
    } catch (RejectedExecutionException e) {
      androidOptions
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "Failed to call the executor. Cached events will not be sent. Did you call Sentry.close()?",
              e);
    } catch (Throwable e) {
      androidOptions
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to call the executor. Cached events will not be sent", e);
    }
  }
}
