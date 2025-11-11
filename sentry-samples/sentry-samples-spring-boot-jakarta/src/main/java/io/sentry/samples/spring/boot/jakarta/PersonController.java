package io.sentry.samples.spring.boot.jakarta;

import io.sentry.ISpan;
import io.sentry.Sentry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/person/")
public class PersonController {
  private final PersonService personService;
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonController.class);

  public PersonController(PersonService personService) {
    this.personService = personService;
  }

  @GetMapping("{id}")
  Person person(@PathVariable Long id) {
    Sentry.addFeatureFlag("transaction-feature-flag", true);
    ISpan currentSpan = Sentry.getSpan();
    ISpan sentrySpan = currentSpan.startChild("spanCreatedThroughSentryApi");
    try {
      Sentry.logger().warn("warn Sentry logging");
      Sentry.logger().error("error Sentry logging");
      Sentry.logger().info("hello %s %s", "there", "world!");
      Sentry.addFeatureFlag("my-feature-flag", true);
      LOGGER.error("Trying person with id={}", id, new RuntimeException("error while loading"));
      throw new IllegalArgumentException("Something went wrong [id=" + id + "]");
    } finally {
      sentrySpan.finish();
    }
  }

  @PostMapping
  Person create(@RequestBody Person person) {
    ISpan currentSpan = Sentry.getSpan();
    ISpan sentrySpan = currentSpan.startChild("spanCreatedThroughSentryApi");
    try {
      return personService.create(person);
    } finally {
      sentrySpan.finish();
    }
  }
}
