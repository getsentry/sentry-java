package io.sentry.spring.boot4;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IScopes;
import io.sentry.spring7.tracing.SentrySpanClientHttpRequestInterceptor;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.restclient.RestTemplateCustomizer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

@Open
class SentrySpanRestTemplateCustomizer implements RestTemplateCustomizer {
  private final @NotNull SentrySpanClientHttpRequestInterceptor interceptor;

  public SentrySpanRestTemplateCustomizer(final @NotNull IScopes scopes) {
    this.interceptor = new SentrySpanClientHttpRequestInterceptor(scopes);
  }

  @Override
  public void customize(final @NotNull RestTemplate restTemplate) {
    final List<ClientHttpRequestInterceptor> existingInterceptors = restTemplate.getInterceptors();
    if (!existingInterceptors.contains(this.interceptor)) {
      final List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
      interceptors.add(this.interceptor);
      interceptors.addAll(existingInterceptors);
      restTemplate.setInterceptors(interceptors);
    }
  }
}
