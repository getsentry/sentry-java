package io.sentry.samples.spring7;

import io.sentry.IScopes;
import io.sentry.spring7.tracing.SentrySpanClientHttpRequestInterceptor;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan("io.sentry.samples.spring7")
@EnableWebMvc
public class WebConfig {

  /**
   * Creates a {@link RestTemplate} which calls are intercepted with {@link
   * SentrySpanClientHttpRequestInterceptor} to create spans around HTTP calls.
   *
   * @param scopes - sentry scopes
   * @return RestTemplate
   */
  @Bean
  RestTemplate restTemplate(IScopes scopes) {
    RestTemplate restTemplate = new RestTemplate();
    SentrySpanClientHttpRequestInterceptor sentryRestTemplateInterceptor =
        new SentrySpanClientHttpRequestInterceptor(scopes);
    restTemplate.setInterceptors(Collections.singletonList(sentryRestTemplateInterceptor));
    return restTemplate;
  }
}
