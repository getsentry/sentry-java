package io.sentry.spring.reactive;

import io.sentry.protocol.User;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * Provides user information that's set on {@link io.sentry.SentryEvent}.
 *
 * <p>Out of the box Spring integration configures single {@link SentryReactiveUserProvider}.
 */
@FunctionalInterface
public interface SentryReactiveUserProvider {
  @Nullable
  User provideUser(ServerHttpRequest request);
}
