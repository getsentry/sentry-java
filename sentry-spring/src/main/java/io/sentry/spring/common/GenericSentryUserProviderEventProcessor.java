package io.sentry.spring.common;

import io.sentry.EventProcessor;
import io.sentry.SentryEvent;
import io.sentry.protocol.User;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Abstracts common logic for UserProviderEventProcessor's */
public abstract class GenericSentryUserProviderEventProcessor implements EventProcessor {

  @Override
  public SentryEvent process(final @NotNull SentryEvent event, final @Nullable Object hint) {
    final User user = provideUser();
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

  protected abstract User provideUser();
}
