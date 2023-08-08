package io.sentry.spring.boot.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.spring.jakarta.tracing.SentrySpanClientWebRequestFilter;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.web.reactive.function.client.WebClient;

@Open
class SentrySpanWebClientCustomizer implements WebClientCustomizer {
  private final @NotNull SentrySpanClientWebRequestFilter filter;

  public SentrySpanWebClientCustomizer(final @NotNull IHub hub) {
    this.filter = new SentrySpanClientWebRequestFilter(hub);
  }

  @Override
  public void customize(WebClient.Builder webClientBuilder) {
    webClientBuilder.filter(this.filter);
  }
}
