package io.sentry.spring;

import io.sentry.core.protocol.User;
import org.jetbrains.annotations.Nullable;

/**
 * Provides user information that's set on {@link io.sentry.core.SentryEvent} using {@link
 * SentryUserProviderEventProcessor}.
 *
 * <p>Out of the box Spring integration configures single {@link SentryUserProvider} - {@link
 * HttpServletRequestSentryUserProvider}.
 */
@FunctionalInterface
public interface SentryUserProvider {
  @Nullable
  User provideUser();
}
