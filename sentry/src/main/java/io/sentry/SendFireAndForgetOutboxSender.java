package io.sentry;

import io.sentry.util.Objects;
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
      final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    Objects.requireNonNull(scopes, "Scopes are required");
    Objects.requireNonNull(options, "SentryOptions is required");

    final String dirPath = sendFireAndForgetDirPath.getDirPath();
    if (dirPath == null || !hasValidPath(dirPath, options.getLogger())) {
      if (options.getLogger().isEnabled(SentryLevel.ERROR)) {
        options.getLogger().log(SentryLevel.ERROR, "No outbox dir path is defined in options.");
      }
      return null;
    }

    final OutboxSender outboxSender =
        new OutboxSender(
            scopes,
            options.getEnvelopeReader(),
            options.getSerializer(),
            options.getLogger(),
            options.getFlushTimeoutMillis(),
            options.getMaxQueueSize());

    return processDir(outboxSender, dirPath, options.getLogger());
  }
}
