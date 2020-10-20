package io.sentry.spring;

import io.sentry.protocol.User;
import io.sentry.spring.common.GenericSentryUserProviderEventProcessor;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class SentryUserProviderEventProcessor
    extends GenericSentryUserProviderEventProcessor {
  private final @NotNull SentryUserProvider sentryUserProvider;

  public SentryUserProviderEventProcessor(final @NotNull SentryUserProvider sentryUserProvider) {
    this.sentryUserProvider =
        Objects.requireNonNull(sentryUserProvider, "sentryUserProvider is required");
  }

  @Override
  protected User provideUser() {
    return sentryUserProvider.provideUser();
  }

  @NotNull
  @ApiStatus.Internal
  public SentryUserProvider getSentryUserProvider() {
    return sentryUserProvider;
  }
}
