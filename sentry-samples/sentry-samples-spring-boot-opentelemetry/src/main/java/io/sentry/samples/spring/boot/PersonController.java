package io.sentry.samples.spring.boot;

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
    LOGGER.info("Loading person with id={}", id);
    throw new IllegalArgumentException("Something went wrong [id=" + id + "]");
  }

  @PostMapping
  Person create(@RequestBody Person person) {
    return personService.create(person);
  }
}
