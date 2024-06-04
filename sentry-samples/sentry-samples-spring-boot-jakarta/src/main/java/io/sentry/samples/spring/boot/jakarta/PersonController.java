package io.sentry.samples.spring.boot.jakarta;

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
  private final JmsService jmsService;
  private final AmqpService amqpService;
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonController.class);

  public PersonController(PersonService personService, JmsService jmsService, AmqpService amqpService) {
    this.personService = personService;
    this.jmsService = jmsService;
    this.amqpService = amqpService;
  }

  @GetMapping("{id}")
  Person person(@PathVariable Long id) {
    LOGGER.error("Trying person with id={}", id, new RuntimeException("error while loading"));
    throw new IllegalArgumentException("Something went wrong [id=" + id + "]");
  }

  @PostMapping
  Person create(@RequestBody Person person) {
//    jmsService.sendMessage(person.getFirstName());
    amqpService.sendMessage(person.getFirstName());
    return personService.create(person);
  }
}
