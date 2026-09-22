package io.sentry.samples.spring.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
public class TodoController {
  private final RestTemplate restTemplate;
  private final WebClient webClient;
  private final OkHttpClient okHttpClient;
  private final ObjectMapper objectMapper;

  public TodoController(
      RestTemplate restTemplate,
      WebClient webClient,
      OkHttpClient okHttpClient,
      ObjectMapper objectMapper) {
    this.restTemplate = restTemplate;
    this.webClient = webClient;
    this.okHttpClient = okHttpClient;
    this.objectMapper = objectMapper;
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

  @GetMapping("/todo-okhttp/{id}")
  Todo todoOkHttp(@PathVariable Long id) {
    final Request request =
        new Request.Builder().url("https://jsonplaceholder.typicode.com/todos/" + id).build();
    try (Response response = okHttpClient.newCall(request).execute()) {
      return objectMapper.readValue(response.body().byteStream(), Todo.class);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
