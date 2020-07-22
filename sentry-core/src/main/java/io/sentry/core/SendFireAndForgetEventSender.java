package io.sentry.core;

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
    if (!hasValidPath(dirPath, options.getLogger())) {
      options.getLogger().log(SentryLevel.ERROR, "No cache dir path is defined in options.");
      return null;
    }

    final SendCachedEvent sender =
        new SendCachedEvent(
            options.getSerializer(), hub, options.getLogger(), options.getFlushTimeoutMillis());

    return processDir(sender, dirPath, options.getLogger());
  }
}
