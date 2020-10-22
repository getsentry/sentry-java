package io.sentry.spring;

import io.sentry.EventProcessor;
import io.sentry.SentryEvent;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryUserProviderEventProcessor implements EventProcessor {
  private final @NotNull SentryUserProvider sentryUserProvider;

  public SentryUserProviderEventProcessor(final @NotNull SentryUserProvider sentryUserProvider) {
    this.sentryUserProvider =
        Objects.requireNonNull(sentryUserProvider, "sentryUserProvider is required");
  }

  @Override
  public SentryEvent process(final @NotNull SentryEvent event, final @Nullable Object hint) {
    final User user = sentryUserProvider.provideUser();
    if (user != null) {
      final User existingUser = Optional.ofNullable(event.getUser()).orElseGet(User::new);

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
      event.setUser(existingUser);
    }
    return event;
  }

  @NotNull
  @ApiStatus.Internal
  public SentryUserProvider getSentryUserProvider() {
    return sentryUserProvider;
  }
}
