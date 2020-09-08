package io.sentry.core;

import io.sentry.core.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SendFireAndForgetOutboxSender
    implements SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetFactory {

  private final @NotNull SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetDirPath
      sendFireAndForgetDirPath;

  public SendFireAndForgetOutboxSender(
      final @NotNull SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetDirPath
              sendFireAndForgetDirPath) {
    this.sendFireAndForgetDirPath =
        Objects.requireNonNull(sendFireAndForgetDirPath, "SendFireAndForgetDirPath is required");
  }

  @Override
  public @Nullable SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForget create(
      final @NotNull IHub hub, final @NotNull SentryOptions options) {
    Objects.requireNonNull(hub, "Hub is required");
    Objects.requireNonNull(options, "SentryOptions is required");

    final String dirPath = sendFireAndForgetDirPath.getDirPath();
    if (!hasValidPath(dirPath, options.getLogger())) {
      options.getLogger().log(SentryLevel.ERROR, "No outbox dir path is defined in options.");
      return null;
    }

    final OutboxSender outboxSender =
        new OutboxSender(
            hub,
            options.getEnvelopeReader(),
            options.getSerializer(),
            options.getLogger(),
            options.getFlushTimeoutMillis());

    return processDir(outboxSender, dirPath, options.getLogger());
  }
}
