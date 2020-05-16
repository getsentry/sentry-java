package io.sentry.core;

import java.io.File;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SendFireAndForgetEnvelopeSender
    implements SendCachedEventFireAndForgetIntegration.SendFireAndForgetFactory {

  private final @NotNull SendCachedEventFireAndForgetIntegration.SendFireAndForgetDirPath
      sendFireAndForgetDirPath;

  public SendFireAndForgetEnvelopeSender(
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
              "No envelope dir path is defined in options, discarding EnvelopeSender.");
      return null;
    }

    final EnvelopeSender envelopeSender =
        new EnvelopeSender(
            hub,
            new EnvelopeReader(),
            options.getSerializer(),
            options.getLogger(),
            options.getFlushTimeoutMillis());
    final File dirFile = new File(dirPath);
    return () -> {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Started processing cached files from %s", dirPath);
      envelopeSender.processDirectory(dirFile);
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Finished processing cached files from %s", dirPath);
    };
  }
}
