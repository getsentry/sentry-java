package io.sentry.spring.reactive;

import io.sentry.protocol.User;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Provides user information that's set on {@link io.sentry.SentryEvent}.
 *
 * <p>Out of the box Spring integration configures single {@link SentryReactiveUserProvider}.
 */
@FunctionalInterface
public interface SentryReactiveUserProvider {

  Mono<User> provideUser(ServerWebExchange request);
}
