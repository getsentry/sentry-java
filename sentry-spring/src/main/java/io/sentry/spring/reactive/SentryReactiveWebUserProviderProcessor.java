package io.sentry.spring.reactive;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.EventProcessor;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.server.reactive.ServerHttpRequest;

/** Attaches information about Reactive HTTP request to {@link SentryEvent}. */
@Open
public class SentryReactiveWebUserProviderProcessor implements EventProcessor {

  private final @NotNull ServerHttpRequest request;
  private final @NotNull SentryOptions options;
  private final @NotNull SentryReactiveUserProvider userProvider;

  public SentryReactiveWebUserProviderProcessor(
      final @NotNull ServerHttpRequest request,
      final @NotNull SentryOptions options,
      SentryReactiveUserProvider userProvider) {
    this.request = Objects.requireNonNull(request, "request is required");
    this.options = Objects.requireNonNull(options, "options are required");
    this.userProvider = Objects.requireNonNull(userProvider, "options are required");
  }

  @Override
  public @NotNull SentryEvent process(
      final @NotNull SentryEvent event, final @Nullable Object hint) {
    if (options.isSendDefaultPii()) {
      event.setUser(userProvider.provideUser(request));
    }
    return event;
  }
}
