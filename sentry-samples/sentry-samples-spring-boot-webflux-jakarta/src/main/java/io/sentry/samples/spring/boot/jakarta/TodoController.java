package io.sentry.samples.spring.boot.jakarta;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
public class TodoController {
  private final WebClient webClient;

  public TodoController(WebClient webClient) {
    this.webClient = webClient;
  }

  @GetMapping("/todo-webclient/{id}")
  Mono<Todo> todoWebClient(@PathVariable Long id) {
    return webClient
        .get()
        .uri("https://jsonplaceholder.typicode.com/todos/{id}", id)
        .retrieve()
        .bodyToMono(Todo.class)
        .map(response -> response);
  }
}
