package io.sentry.samples.spring.boot;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.sentry.ISpan;
import io.sentry.Sentry;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
public class TodoController {
  private final RestTemplate restTemplate;
  private final WebClient webClient;
  private final Tracer tracer;

  public TodoController(RestTemplate restTemplate, WebClient webClient, Tracer tracer) {
    this.restTemplate = restTemplate;
    this.webClient = webClient;
    this.tracer = tracer;
  }

  @GetMapping("/todo/{id}")
  Todo todo(@PathVariable Long id) {
    Span otelSpan = tracer.spanBuilder("todoSpanOtelApi").startSpan();
    try (final @NotNull Scope spanScope = otelSpan.makeCurrent()) {
      ISpan sentrySpan = Sentry.getSpan().startChild("todoSpanSentryApi");
      try {
        return restTemplate.getForObject(
            "https://jsonplaceholder.typicode.com/todos/{id}", Todo.class, id);
      } finally {
        sentrySpan.finish();
      }
    } finally {
      otelSpan.end();
    }
  }

  @GetMapping("/todo-webclient/{id}")
  Todo todoWebClient(@PathVariable Long id) {
    return webClient
        .get()
        .uri("https://jsonplaceholder.typicode.com/todos/{id}", id)
        .retrieve()
        .bodyToMono(Todo.class)
        .block();
  }
}
