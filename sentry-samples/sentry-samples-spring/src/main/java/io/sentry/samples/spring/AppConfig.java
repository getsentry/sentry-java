package io.sentry.samples.spring;

import io.sentry.IScopes;
import io.sentry.spring.SentryUserFilter;
import io.sentry.spring.SentryUserProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(SentryConfig.class)
public class AppConfig {

  @Autowired private ApplicationContext applicationContext;

  @Bean
  SentryUserFilter sentryUserFilter(final List<SentryUserProvider> sentryUserProviders) {
    return new SentryUserFilter(applicationContext.getBean(IScopes.class), sentryUserProviders);
  }
}
