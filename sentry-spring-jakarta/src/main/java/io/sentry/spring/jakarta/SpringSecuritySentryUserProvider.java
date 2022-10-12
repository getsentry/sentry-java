package io.sentry.spring.jakarta;

import io.sentry.SentryOptions;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves user information from Spring Security {@link Authentication} obtained via {@link
 * SecurityContextHolder}.
 */
public final class SpringSecuritySentryUserProvider implements SentryUserProvider {
  private final @NotNull SentryOptions options;

  public SpringSecuritySentryUserProvider(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "options is required");
  }

  @Override
  public @Nullable User provideUser() {
    if (options.isSendDefaultPii()) {
      final SecurityContext context = SecurityContextHolder.getContext();
      if (context != null && context.getAuthentication() != null) {
        final User user = new User();
        user.setUsername(context.getAuthentication().getName());
        return user;
      }
    }
    return null;
  }
}
