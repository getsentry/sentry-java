package io.sentry.spring.jakarta;

import io.sentry.protocol.User;
import org.jetbrains.annotations.Nullable;

/**
 * Out of the box Spring integration configures single {@link SentryUserProvider} - {@link
 * HttpServletRequestSentryUserProvider}.
 */
@FunctionalInterface
public interface SentryUserProvider {
  @Nullable
  User provideUser();
}
