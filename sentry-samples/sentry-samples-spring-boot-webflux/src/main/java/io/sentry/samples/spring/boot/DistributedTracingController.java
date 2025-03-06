package io.sentry.samples.spring.boot;

import java.nio.charset.Charset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/tracing/")
public class DistributedTracingController {
  private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTracingController.class);
  private final WebClient webClient;

  public DistributedTracingController(WebClient webClient) {
    this.webClient = webClient;
  }

  @GetMapping("{id}")
  Mono<Person> person(@PathVariable Long id) {
    return webClient
        .get()
        .uri("http://localhost:8080/person/{id}", id)
        .header(
            HttpHeaders.AUTHORIZATION,
            "Basic " + HttpHeaders.encodeBasicAuth("user", "password", Charset.defaultCharset()))
        .retrieve()
        .bodyToMono(Person.class)
        .map(response -> response);
  }

  @PostMapping
  Mono<Person> create(@RequestBody Person person) {
    return webClient
        .post()
        .uri("http://localhost:8080/person/")
        .header(
            HttpHeaders.AUTHORIZATION,
            "Basic " + HttpHeaders.encodeBasicAuth("user", "password", Charset.defaultCharset()))
        .body(Mono.just(person), Person.class)
        .retrieve()
        .bodyToMono(Person.class)
        .map(response -> response);
  }
}
