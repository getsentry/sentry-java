package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.IpAddressUtils;
import io.sentry.Scope;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Sets the {@link User} on the {@link Scope} with information retrieved from {@link
 * SentryUserProvider}s.
 */
@Open
public class SentryUserFilter implements Filter {
  private final @NotNull IHub hub;
  private final @NotNull List<SentryUserProvider> sentryUserProviders;

  public SentryUserFilter(
      final @NotNull IHub hub, final @NotNull List<SentryUserProvider> sentryUserProviders) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.sentryUserProviders =
        Objects.requireNonNull(sentryUserProviders, "sentryUserProviders list is required");
  }

  @Override
  public void doFilter(
      final @NotNull ServletRequest request,
      final @NotNull ServletResponse response,
      final @NotNull FilterChain chain)
      throws IOException, ServletException {
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
    hub.configureScope(scope -> scope.setUser(user));
    chain.doFilter(request, response);
  }

  private void apply(User existingUser, User user) {
    if (user != null) {
      Optional.ofNullable(user.getEmail()).ifPresent(existingUser::setEmail);
      Optional.ofNullable(user.getId()).ifPresent(existingUser::setId);
      Optional.ofNullable(user.getIpAddress()).ifPresent(existingUser::setIpAddress);
      Optional.ofNullable(user.getUsername()).ifPresent(existingUser::setUsername);
      if (user.getOthers() != null && !user.getOthers().isEmpty()) {
        if (existingUser.getOthers() == null) {
          existingUser.setOthers(new ConcurrentHashMap<>());
        }
        for (Map.Entry<String, String> entry : user.getOthers().entrySet()) {
          existingUser.getOthers().put(entry.getKey(), entry.getValue());
        }
      }
    }
  }

  @VisibleForTesting
  public List<SentryUserProvider> getSentryUserProviders() {
    return sentryUserProviders;
  }
}
