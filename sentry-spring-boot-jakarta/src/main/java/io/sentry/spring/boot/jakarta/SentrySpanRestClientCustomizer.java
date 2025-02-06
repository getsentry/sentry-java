package io.sentry.spring.boot.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IScopes;
import io.sentry.spring.jakarta.tracing.SentrySpanClientHttpRequestInterceptor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.web.client.RestClient;

@Open
class SentrySpanRestClientCustomizer implements RestClientCustomizer {
  private final @NotNull SentrySpanClientHttpRequestInterceptor interceptor;

  public SentrySpanRestClientCustomizer(final @NotNull IScopes scopes) {
    this.interceptor = new SentrySpanClientHttpRequestInterceptor(scopes, false);
  }

  @Override
  public void customize(final @NotNull RestClient.Builder restClientBuilder) {
    restClientBuilder.requestInterceptors(
        clientHttpRequestInterceptors -> {
          // As the SentrySpanClientHttpRequestInterceptor is being created in this class, this
          // might not work
          // if somebody registers it from an outside.
          if (!clientHttpRequestInterceptors.contains(interceptor)) {
            clientHttpRequestInterceptors.add(interceptor);
          }
        });
  }
}
