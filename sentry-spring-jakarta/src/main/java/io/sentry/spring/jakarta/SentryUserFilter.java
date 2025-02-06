package io.sentry.spring.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IScope;
import io.sentry.IScopes;
import io.sentry.IpAddressUtils;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sets the {@link User} on the {@link IScope} with information retrieved from {@link
 * SentryUserProvider}s.
 */
@Open
public class SentryUserFilter extends OncePerRequestFilter {
  private final @NotNull IScopes scopes;
  private final @NotNull List<SentryUserProvider> sentryUserProviders;

  public SentryUserFilter(
      final @NotNull IScopes scopes, final @NotNull List<SentryUserProvider> sentryUserProviders) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
    this.sentryUserProviders =
        Objects.requireNonNull(sentryUserProviders, "sentryUserProviders list is required");
  }

  @Override
  protected void doFilterInternal(
      final @NotNull HttpServletRequest request,
      final @NotNull HttpServletResponse response,
      final @NotNull FilterChain chain)
      throws ServletException, IOException {
    final User user = new User();
    for (final SentryUserProvider provider : sentryUserProviders) {
      apply(user, provider.provideUser());
    }
    if (scopes.getOptions().isSendDefaultPii()) {
      if (IpAddressUtils.isDefault(user.getIpAddress())) {
        // unset {{auto}} as it would set the server's ip address as a user ip address
        user.setIpAddress(null);
      }
    }
    scopes.setUser(user);
    chain.doFilter(request, response);
  }

  private void apply(final @NotNull User existingUser, final @Nullable User userFromProvider) {
    if (userFromProvider != null) {
      Optional.ofNullable(userFromProvider.getEmail()).ifPresent(existingUser::setEmail);
      Optional.ofNullable(userFromProvider.getId()).ifPresent(existingUser::setId);
      Optional.ofNullable(userFromProvider.getIpAddress()).ifPresent(existingUser::setIpAddress);
      Optional.ofNullable(userFromProvider.getUsername()).ifPresent(existingUser::setUsername);
      if (userFromProvider.getData() != null && !userFromProvider.getData().isEmpty()) {
        Map<String, String> existingUserData = existingUser.getData();
        if (existingUserData == null) {
          existingUserData = new ConcurrentHashMap<>();
        }
        for (final Map.Entry<String, String> entry : userFromProvider.getData().entrySet()) {
          existingUserData.put(entry.getKey(), entry.getValue());
        }
        existingUser.setData(existingUserData);
      }
    }
  }

  @VisibleForTesting
  public @NotNull List<SentryUserProvider> getSentryUserProviders() {
    return sentryUserProviders;
  }
}
