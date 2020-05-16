package io.sentry.core;

import java.io.File;
import org.jetbrains.annotations.NotNull;

final class SendFireAndForgetEventSender
    implements SendCachedEventFireAndForgetIntegration.SendFireAndForgetFactory {

  private final @NotNull SendCachedEventFireAndForgetIntegration.SendFireAndForgetDirPath
      sendFireAndForgetDirPath;

  SendFireAndForgetEventSender(
      final @NotNull SendCachedEventFireAndForgetIntegration.SendFireAndForgetDirPath
              sendFireAndForgetDirPath) {
    this.sendFireAndForgetDirPath = sendFireAndForgetDirPath;
  }

  @Override
  public SendCachedEventFireAndForgetIntegration.SendFireAndForget create(
      final @NotNull IHub hub, final @NotNull SentryOptions options) {
    final String dirPath = sendFireAndForgetDirPath.getDirPath();
    if (dirPath == null) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "No cache dir path is defined in options, discarding SendCachedEvent.");
      return null;
    }

    final SendCachedEvent sender =
        new SendCachedEvent(
            options.getSerializer(), hub, options.getLogger(), options.getFlushTimeoutMillis());

    final File dirFile = new File(dirPath);
    return () -> {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Started processing cached files from %s", dirPath);

      sender.processDirectory(dirFile);

      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Finished processing cached files from %s", dirPath);
    };
  }
}
