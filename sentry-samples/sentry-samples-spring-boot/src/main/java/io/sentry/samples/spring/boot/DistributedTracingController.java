package io.sentry.samples.spring.boot;

import java.nio.charset.Charset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/tracing/")
public class DistributedTracingController {
  private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTracingController.class);
  private final RestTemplate restTemplate;

  public DistributedTracingController(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @GetMapping("{id}")
  Person person(@PathVariable Long id) {
    return restTemplate
        .exchange(
            "http://localhost:8080/person/" + id,
            HttpMethod.GET,
            new HttpEntity<Object>(createHeaders()),
            Person.class)
        .getBody();
  }

  @PostMapping
  Person create(@RequestBody Person person) {
    return restTemplate
        .exchange(
            "http://localhost:8080/person/",
            HttpMethod.POST,
            new HttpEntity<Person>(person, createHeaders()),
            Person.class)
        .getBody();
  }

  private HttpHeaders createHeaders() {
    HttpHeaders headers = new HttpHeaders();

    headers.setBasicAuth("user", "password", Charset.defaultCharset());

    return headers;
  }
}
