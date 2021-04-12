package io.sentry.spring;

import io.sentry.protocol.User;
import org.jetbrains.annotations.Nullable;

/**
 * Provides user information that's set on {@link io.sentry.Scope} using {@link SentryUserFilter}.
 *
 * <p>Out of the box Spring integration configures single {@link SentryUserProvider} - {@link
 * HttpServletRequestSentryUserProvider}.
 */
@FunctionalInterface
public interface SentryUserProvider {
  @Nullable
  User provideUser();
}
