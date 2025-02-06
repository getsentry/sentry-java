package io.sentry.samples.spring.jakarta.web;

import io.sentry.spring.jakarta.tracing.SentrySpan;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class PersonService {
  private final RestTemplate restTemplate;

  public PersonService(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @SentrySpan
  @SuppressWarnings("unchecked")
  Person find(Long id) {
    Map<String, Object> result =
        restTemplate.getForObject("https://jsonplaceholder.typicode.com/users/{id}", Map.class, id);
    String name = (String) result.get("name");
    if (name != null) {
      String[] nameParts = name.split(" ");
      return new Person(nameParts[0], nameParts[1]);
    } else {
      return null;
    }
  }

  @SentrySpan
  Person create(Person person) {
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      // ignored
    }
    return person;
  }
}
