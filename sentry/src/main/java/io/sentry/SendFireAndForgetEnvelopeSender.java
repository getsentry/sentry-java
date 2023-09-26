package io.sentry;

import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SendFireAndForgetEnvelopeSender
    implements SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetFactory {

  private final @NotNull SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetDirPath
      sendFireAndForgetDirPath;

  public SendFireAndForgetEnvelopeSender(
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
    if (dirPath == null || !hasValidPath(dirPath, options.getLogger())) {
      options.getLogger().log(SentryLevel.ERROR, "No cache dir path is defined in options.");
      return null;
    }

    final EnvelopeSender envelopeSender =
        new EnvelopeSender(
            hub, options.getSerializer(), options.getLogger(), options.getFlushTimeoutMillis());

    return processDir(envelopeSender, dirPath, options.getLogger());
  }
}
