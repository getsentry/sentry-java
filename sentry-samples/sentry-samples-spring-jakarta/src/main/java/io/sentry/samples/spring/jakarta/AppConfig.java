package io.sentry.samples.spring.jakarta;

import io.sentry.IScopes;
import io.sentry.spring.jakarta.SentryUserFilter;
import io.sentry.spring.jakarta.SentryUserProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(SentryConfig.class)
public class AppConfig {

  @Bean
  SentryUserFilter sentryUserFilter(
      final IScopes scopes, final List<SentryUserProvider> sentryUserProviders) {
    return new SentryUserFilter(scopes, sentryUserProviders);
  }
}
