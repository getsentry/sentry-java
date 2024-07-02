package io.sentry.spring.boot.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.spring.jakarta.tracing.SentrySpanClientHttpRequestInterceptor;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

@Open
class SentrySpanRestTemplateCustomizer implements RestTemplateCustomizer {
  private final @NotNull SentrySpanClientHttpRequestInterceptor interceptor;

  public SentrySpanRestTemplateCustomizer(final @NotNull IHub hub) {
    this.interceptor = new SentrySpanClientHttpRequestInterceptor(hub);
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
