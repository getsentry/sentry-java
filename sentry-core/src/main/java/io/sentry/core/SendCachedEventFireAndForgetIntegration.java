package io.sentry.core;

import org.jetbrains.annotations.NotNull;

/** Sends cached events over when your App. is starting. */
public final class SendCachedEventFireAndForgetIntegration implements Integration {

  private final SendFireAndForgetFactory factory;

  public interface SendFireAndForget {
    void send();
  }

  public interface SendFireAndForgetDirPath {
    String getDirPath();
  }

  public interface SendFireAndForgetFactory {
    SendFireAndForget create(IHub hub, SentryOptions options);
  }

  public SendCachedEventFireAndForgetIntegration(SendFireAndForgetFactory factory) {
    this.factory = factory;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public final void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    final String cachedDir = options.getCacheDirPath();
    if (cachedDir == null) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "No cache dir path is defined in options to SendCachedEventFireAndForgetIntegration.");
      return;
    }

    final SendFireAndForget sender = factory.create(hub, options);

    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                try {
                  sender.send();
                } catch (Exception e) {
                  options
                      .getLogger()
                      .log(SentryLevel.ERROR, "Failed trying to send cached events.", e);
                }
              });

      options
          .getLogger()
          .log(SentryLevel.DEBUG, "SendCachedEventFireAndForgetIntegration installed.");
    } catch (Exception e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to call the executor. Cached events will not be sent", e);
    }
  }
}
