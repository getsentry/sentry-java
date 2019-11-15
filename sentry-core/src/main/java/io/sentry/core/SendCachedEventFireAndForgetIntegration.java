package io.sentry.core;

import static io.sentry.core.ILogger.logIfNotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;

final class SendCachedEventFireAndForgetIntegration implements Integration {

  private SendFireAndForgetFactory factory;

  interface SendFireAndForget {
    void send();
  }

  interface SendFireAndForgetFactory {
    SendFireAndForget create(IHub hub, SentryOptions options);
  }

  SendCachedEventFireAndForgetIntegration(SendFireAndForgetFactory factory) {
    this.factory = factory;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public void register(@NotNull IHub hub, @NotNull SentryOptions options) {
    String cachedDir = options.getCacheDirPath();
    if (cachedDir == null) {
      logIfNotNull(
          options.getLogger(), SentryLevel.WARNING, "No cache dir path is defined in options.");
      return;
    }

    SendFireAndForget sender = factory.create(hub, options);

    try {
      ExecutorService es = Executors.newSingleThreadExecutor();
      es.submit(
          () -> {
            try {
              sender.send();
              logIfNotNull(
                  options.getLogger(),
                  SentryLevel.DEBUG,
                  "Finished processing cached files from %s",
                  cachedDir);
            } catch (Exception e) {
              logIfNotNull(
                  options.getLogger(),
                  SentryLevel.ERROR,
                  "Failed trying to send cached events.",
                  e);
            }
          });
      logIfNotNull(
          options.getLogger(),
          SentryLevel.DEBUG,
          "Scheduled sending cached files from %s",
          cachedDir);
      es.shutdown();
    } catch (Exception e) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.ERROR,
          "Failed to call the executor. Cached events will not be sent",
          e);
    }
  }
}
