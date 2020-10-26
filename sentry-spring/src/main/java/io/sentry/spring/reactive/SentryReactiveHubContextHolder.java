package io.sentry.spring.reactive;

import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public final class SentryReactiveHubContextHolder {
  private static final Class<?> CONTEXT_KEY = SentryReactiveHubContextHolder.class;

  /**
   * Gets the Sentry {@code Mono<SentryReactiveHubAdapter>} from Reactor {@link
   * SentryReactiveHubAdapter}
   *
   * @return the {@code Mono<SentryReactiveHubAdapter>}
   */
  public static Mono<SentryReactiveHubAdapter> getHubContext() {
    return Mono.subscriberContext()
        .filter(c -> c.hasKey(CONTEXT_KEY))
        .map(c -> c.<SentryReactiveHubAdapter>get(CONTEXT_KEY));
  }

  static Context withSentryHub(final @NotNull SentryReactiveHubAdapter adapter) {
    return Context.of(CONTEXT_KEY, adapter);
  }
}
