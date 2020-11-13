package io.sentry.samples.spring.boot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class TodoController {
  private final RestTemplate restTemplate;

  public TodoController(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @GetMapping("/todo/{id}")
  Todo todo(@PathVariable Long id) {
    return restTemplate.getForObject(
        "https://jsonplaceholder.typicode.com/todos/{id}", Todo.class, id);
  }
}
