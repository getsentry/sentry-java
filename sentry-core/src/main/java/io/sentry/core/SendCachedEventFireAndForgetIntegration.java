package io.sentry.core;

import static io.sentry.core.ILogger.logIfNotNull;

import java.io.File;
import java.util.concurrent.*;
import org.jetbrains.annotations.NotNull;

final class SendCachedEventFireAndForgetIntegration implements Integration {

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public void register(@NotNull IHub hub, @NotNull SentryOptions options) {
    String cachedDir = options.getCacheDirPath();
    if (cachedDir == null) {
      logIfNotNull(
          options.getLogger(), SentryLevel.WARNING, "No cache dir path is defined in options.");
      return;
    }

    SendCachedEvent sender = new SendCachedEvent(options.getSerializer(), hub, options.getLogger());
    File outbox = new File(cachedDir);

    try {
      ExecutorService es = Executors.newSingleThreadExecutor();
      es.submit(
          () -> {
            try {
              sender.sendCachedFiles(outbox);
            } catch (Exception e) {
              logIfNotNull(
                  options.getLogger(),
                  SentryLevel.ERROR,
                  "Failed trying to send cached events at %s",
                  e,
                  outbox);
            }
          });
      es.shutdown();
    } catch (Exception e) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.ERROR,
          "Failed to call the executor. Cached events will not be sent",
          e,
          outbox);
    }
  }
}
