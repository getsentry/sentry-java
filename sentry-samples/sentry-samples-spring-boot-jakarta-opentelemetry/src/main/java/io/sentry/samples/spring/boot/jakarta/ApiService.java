package io.sentry.samples.spring.boot.jakarta;

import io.sentry.spring.jakarta.tracing.SentrySpan;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ApiService {

  private final RestClient restClient;

  public ApiService(RestClient restClient) {
    this.restClient = restClient;
  }

  @SentrySpan("annotation-span")
  void apiRequest(final @NotNull String name) {
    //    restClient.get().uri("http://localhost:8000?q={name}",
    // name).retrieve().body(String.class);
    restClient.get().uri("http://localhost:8081/articles").retrieve().body(String.class);
  }
}
