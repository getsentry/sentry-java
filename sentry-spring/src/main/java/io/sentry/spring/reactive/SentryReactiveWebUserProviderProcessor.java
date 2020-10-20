package io.sentry.spring.reactive;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.SentryEvent;
import io.sentry.protocol.User;
import io.sentry.spring.common.GenericSentryUserProviderEventProcessor;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.server.ServerWebExchange;

/** Attaches information about User in Reactive HTTP request to {@link SentryEvent}. */
@Open
public class SentryReactiveWebUserProviderProcessor
    extends GenericSentryUserProviderEventProcessor {

  private final @NotNull ServerWebExchange request;
  private final @NotNull SentryReactiveUserProvider userProvider;

  public SentryReactiveWebUserProviderProcessor(
      @NotNull ServerWebExchange request, @NotNull SentryReactiveUserProvider userProvider) {
    this.request = Objects.requireNonNull(request, "request is required");
    this.userProvider = Objects.requireNonNull(userProvider, "options are required");
  }

  @Override
  protected User provideUser() {
    return userProvider.provideUser(request);
  }
}
