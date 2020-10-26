package sentry.samples.webflux;

import io.sentry.protocol.User;
import io.sentry.spring.reactive.SentryReactiveUserProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Custom {@link SentryReactiveUserProvider} implementation may get user information from external
 * source (as Database).
 */
@Component
public class CustomUserProvider implements SentryReactiveUserProvider {

  @Override
  public Mono<User> provideUser(ServerWebExchange request) {

    return Mono.fromSupplier(
        () -> {
          final User user = new User();
          user.setEmail("user@example.org");
          return user;
        });
  }
}
