package io.sentry.samples.spring.jakarta.web;

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
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonController.class);

  private final PersonService personService;

  public PersonController(PersonService personService) {
    this.personService = personService;
  }

  @GetMapping("{id}")
  Person person(@PathVariable("id") Long id) {
    Sentry.logger().warn("warn Sentry logging");
    Sentry.logger().error("error Sentry logging");
    Sentry.logger().info("hello %s %s", "there", "world!");
    Sentry.addFeatureFlag("my-feature-flag", true);
    LOGGER.info("Loading person with id={}", id);
    if (id > 10L) {
      throw new IllegalArgumentException("Something went wrong [id=" + id + "]");
    } else {
      return personService.find(id);
    }
  }

  @PostMapping
  Person create(@RequestBody Person person) {
    LOGGER.warn("Creating person: {}", person);
    return personService.create(person);
  }
}
