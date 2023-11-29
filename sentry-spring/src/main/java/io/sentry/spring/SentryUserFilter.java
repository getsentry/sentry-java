package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.IScope;
import io.sentry.IpAddressUtils;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
  private final @NotNull IHub hub;
  private final @NotNull List<SentryUserProvider> sentryUserProviders;

  public SentryUserFilter(
      final @NotNull IHub hub, final @NotNull List<SentryUserProvider> sentryUserProviders) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
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
    if (hub.getOptions().isSendDefaultPii()) {
      if (IpAddressUtils.isDefault(user.getIpAddress())) {
        // unset {{auto}} as it would set the server's ip address as a user ip address
        user.setIpAddress(null);
      }
    }
    hub.setUser(user);
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
