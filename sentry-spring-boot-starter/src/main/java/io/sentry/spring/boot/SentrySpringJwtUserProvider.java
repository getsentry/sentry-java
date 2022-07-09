package io.sentry.spring.boot;

import io.sentry.SentryOptions;
import io.sentry.protocol.User;
import io.sentry.spring.SentryUserProvider;
import io.sentry.util.Objects;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public final class SentrySpringJwtUserProvider implements SentryUserProvider {
  private final @NotNull SentryOptions options;

  public SentrySpringJwtUserProvider(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "options is required");
  }

  @Override
  public @Nullable User provideUser() {
    if (options.isSendDefaultPii()) {
      final Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
      if (principal instanceof Jwt) {
        return extraJwtUser((Jwt) principal);
      } else {
        return null;
      }
    }
    return null;
  }

  private User extraJwtUser(Jwt jwt) {
    final User user = new User();
    user.setId(jwt.getSubject());
    user.setEmail(jwt.getClaimAsString("email"));
    user.setUsername(findJwtUsername(jwt));
    user.setOthers(findJwtInfo(jwt));
    return user;
  }

  private @Nullable String findJwtUsername(Jwt jwt) {
    if (jwt.hasClaim("username")) {
      return jwt.getClaimAsString("username");
    } else if (jwt.hasClaim("preferred_username")) {
      return jwt.getClaimAsString("preferred_username");
    } else if (jwt.hasClaim("name")) {
      return jwt.getClaimAsString("name");
    } else if (jwt.hasClaim("email")) {
      return jwt.getClaimAsString("email");
    } else {
      return null;
    }
  }

  private Map<String, String> findJwtInfo(Jwt jwt) {
    final Map<String, String> info = new HashMap<>();
    if (jwt.hasClaim("iss")) {
      info.put("iss", jwt.getClaimAsString("iss"));
    }
    if (jwt.hasClaim("aud")) {
      info.put("aud", jwt.getClaimAsString("aud"));
    }
    if (jwt.hasClaim("exp")) {
      info.put("exp", jwt.getClaimAsString("exp"));
    }
    if (jwt.hasClaim("iat")) {
      info.put("iat", jwt.getClaimAsString("iat"));
    }
    if (jwt.hasClaim("scope")) {
      info.put("scope", jwt.getClaimAsString("scope"));
    }
    return info;
  }
}
