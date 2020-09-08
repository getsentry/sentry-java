package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.core.EventProcessor;
import io.sentry.core.SentryEvent;
import io.sentry.core.SentryOptions;
import io.sentry.core.protocol.User;
import java.security.Principal;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Attaches user information to the {@link SentryEvent}. */
@Open
public class SentryUserHttpServletRequestProcessor implements EventProcessor {
  private final @Nullable Principal principal;
  private final @Nullable String ipAddress;
  private final @NotNull SentryOptions options;

  public SentryUserHttpServletRequestProcessor(
      final @Nullable Principal principal,
      final @Nullable String ipAddress,
      final @NotNull SentryOptions options) {
    this.principal = principal;
    this.ipAddress = ipAddress;
    this.options = options;
  }

  @Override
  public SentryEvent process(final @NotNull SentryEvent event, final @Nullable Object hint) {
    if (options.isSendDefaultPii()) {
      final User user = Optional.ofNullable(event.getUser()).orElseGet(User::new);

      if (ipAddress != null) {
        user.setIpAddress(ipAddress);
      }
      if (principal != null) {
        user.setUsername(principal.getName());
      }

      event.setUser(user);
    }
    return event;
  }
}
