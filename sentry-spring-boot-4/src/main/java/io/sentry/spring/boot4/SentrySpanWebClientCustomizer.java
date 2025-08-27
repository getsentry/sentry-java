package io.sentry.spring.boot4;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IScopes;
import io.sentry.spring7.tracing.SentrySpanClientWebRequestFilter;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.web.reactive.function.client.WebClient;

@Open
class SentrySpanWebClientCustomizer implements WebClientCustomizer {
  private final @NotNull SentrySpanClientWebRequestFilter filter;

  public SentrySpanWebClientCustomizer(final @NotNull IScopes scopes) {
    this.filter = new SentrySpanClientWebRequestFilter(scopes);
  }

  @Override
  public void customize(WebClient.Builder webClientBuilder) {
    webClientBuilder.filter(this.filter);
  }
}
