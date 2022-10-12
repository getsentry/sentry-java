package io.sentry.samples.spring.boot.jakarta;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

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
    return webClient
        .get()
        .uri("https://jsonplaceholder.typicode.com/todos/{id}", id)
        .retrieve()
        .bodyToMono(Todo.class)
        .block();
  }
}
