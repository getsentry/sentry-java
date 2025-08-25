package io.sentry.samples.spring.boot4;

import io.opentelemetry.instrumentation.annotations.WithSpan;
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
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/tracing/")
public class DistributedTracingController {
  private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTracingController.class);
  private final RestClient restClient;

  public DistributedTracingController(RestClient restClient) {
    this.restClient = restClient;
  }

  @GetMapping("{id}")
  @WithSpan("tracingSpanThroughOtelAnnotation")
  Person person(@PathVariable Long id) {
    return restClient
        .get()
        .uri("http://localhost:8080/person/{id}", id)
        .header(
            HttpHeaders.AUTHORIZATION,
            "Basic " + HttpHeaders.encodeBasicAuth("user", "password", Charset.defaultCharset()))
        .retrieve()
        .body(Person.class);
  }

  @PostMapping
  Person create(@RequestBody Person person) {
    return restClient
        .post()
        .uri("http://localhost:8080/person/")
        .body(person)
        .header(
            HttpHeaders.AUTHORIZATION,
            "Basic " + HttpHeaders.encodeBasicAuth("user", "password", Charset.defaultCharset()))
        .retrieve()
        .body(Person.class);
  }
}
