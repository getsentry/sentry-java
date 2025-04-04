package io.sentry.samples.spring;

import io.sentry.ScopesAdapter;
import io.sentry.spring.tracing.SentrySpanClientHttpRequestInterceptor;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan("io.sentry.samples.spring.web")
@EnableWebMvc
public class WebConfig {

  /**
   * Creates a {@link RestTemplate} which calls are intercepted with {@link
   * SentrySpanClientHttpRequestInterceptor} to create spans around HTTP calls.
   *
   * @return RestTemplate
   */
  @Bean
  RestTemplate restTemplate() {
    RestTemplate restTemplate = new RestTemplate();
    SentrySpanClientHttpRequestInterceptor sentryRestTemplateInterceptor =
        new SentrySpanClientHttpRequestInterceptor(ScopesAdapter.getInstance());
    restTemplate.setInterceptors(Collections.singletonList(sentryRestTemplateInterceptor));
    return restTemplate;
  }
}
