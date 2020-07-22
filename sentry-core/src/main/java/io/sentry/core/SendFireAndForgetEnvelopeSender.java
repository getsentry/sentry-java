package io.sentry.core;

import io.sentry.core.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SendFireAndForgetEnvelopeSender
    implements SendCachedEventFireAndForgetIntegration.SendFireAndForgetFactory {

  private final @NotNull SendCachedEventFireAndForgetIntegration.SendFireAndForgetDirPath
      sendFireAndForgetDirPath;

  public SendFireAndForgetEnvelopeSender(
      final @NotNull SendCachedEventFireAndForgetIntegration.SendFireAndForgetDirPath
              sendFireAndForgetDirPath) {
    this.sendFireAndForgetDirPath =
        Objects.requireNonNull(sendFireAndForgetDirPath, "SendFireAndForgetDirPath is required");
  }

  @Override
  public @Nullable SendCachedEventFireAndForgetIntegration.SendFireAndForget create(
      final @NotNull IHub hub, final @NotNull SentryOptions options) {
    Objects.requireNonNull(hub, "Hub is required");
    Objects.requireNonNull(options, "SentryOptions is required");

    final String dirPath = sendFireAndForgetDirPath.getDirPath();
    if (!hasValidPath(dirPath, options.getLogger())) {
      options.getLogger().log(SentryLevel.ERROR, "No cache dir path is defined in options.");
      return null;
    }

    final EnvelopeSender envelopeSender =
        new EnvelopeSender(
            hub,
            options.getEnvelopeReader(),
            options.getSerializer(),
            options.getLogger(),
            options.getFlushTimeoutMillis());

    return processDir(envelopeSender, dirPath, options.getLogger());
  }
}
