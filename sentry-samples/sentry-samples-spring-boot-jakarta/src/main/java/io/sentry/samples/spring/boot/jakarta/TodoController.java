package io.sentry.samples.spring.boot.jakarta;

import io.sentry.spring.jakarta.webflux.ReactorUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public class TodoController {
  private final RestTemplate restTemplate;
  private final WebClient webClient;

  public TodoController(RestTemplate restTemplate, WebClient webClient) {
    this.restTemplate = restTemplate;
    this.webClient = webClient;
  }

  @GetMapping("/todo/{id}")
  Todo todo(@PathVariable Long id) {
    return restTemplate.getForObject(
        "https://jsonplaceholder.typicode.com/todos/{id}", Todo.class, id);
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
                            .uri("https://jsonplaceholder.typicode.com/todos/{id}", id)
                            .retrieve()
                            .bodyToMono(Todo.class)
                            .map(response -> response)))
        .block();
  }
}
