package io.sentry.samples.spring.boot.jakarta;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.spring.jakarta.webflux.ReactorUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public class TodoController {
  private final RestTemplate restTemplate;
  private final WebClient webClient;
  private final RestClient restClient;
  private final Tracer tracer;

  @Value("sentry.sample.todo-url")
  public @Nullable String todoUrl;

  public TodoController(
      RestTemplate restTemplate, WebClient webClient, RestClient restClient, Tracer tracer) {
    this.restTemplate = restTemplate;
    this.webClient = webClient;
    this.restClient = restClient;
    this.tracer = tracer;
  }

  @GetMapping("/todo/{id}")
  Todo todo(@PathVariable Long id) {
    Span otelSpan = tracer.spanBuilder("todoSpanOtelApi").startSpan();
    try (final @NotNull Scope spanScope = otelSpan.makeCurrent()) {
      ISpan sentrySpan = Sentry.getSpan().startChild("todoSpanSentryApi");
      try {
        return restTemplate.getForObject(todoUrl + "/todos/{id}", Todo.class, id);
      } finally {
        sentrySpan.finish();
      }
    } finally {
      otelSpan.end();
    }
  }

  @GetMapping("/todo-webclient/{id}")
  Todo todoWebClient(@PathVariable Long id) {
    Hooks.enableAutomaticContextPropagation();
    return ReactorUtils.withSentry(
            Mono.just(true)
                .publishOn(Schedulers.boundedElastic())
                .flatMap(
                    x ->
                        webClient
                            .get()
                            .uri(todoUrl + "/todos/{id}", id)
                            .retrieve()
                            .bodyToMono(Todo.class)
                            .map(response -> response)))
        .block();
  }

  @GetMapping("/todo-restclient/{id}")
  Todo todoRestClient(@PathVariable Long id) {
    Span span = tracer.spanBuilder("todoRestClientSpanOtelApi").startSpan();
    try (final @NotNull Scope spanScope = span.makeCurrent()) {
      ISpan sentrySpan = Sentry.getSpan().startChild("todoRestClientSpanSentryApi");
      try {
        return restClient
            .get()
            .uri(todoUrl + "/todos/{id}", id)
            .retrieve()
            .body(Todo.class);
      } finally {
        sentrySpan.finish();
      }
    } finally {
      span.end();
    }
  }
}
