package io.sentry.samples.spring;

import io.sentry.spring.reactive.SentryReactiveWebHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/person/")
public class PersonController {
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonController.class);

  @GetMapping("{id}")
  Mono<Person> person(@PathVariable Long id) {
    LOGGER.info("Loading person with id={}", id);
    return Mono.defer(() -> Mono.just(id).flatMap(this::getPerson));
  }

  Mono<Person> getPerson(Long id) {
    throw new IllegalArgumentException("Something went wrong [id=" + id + "]");
  }

  @PostMapping
  Mono<Person> create(@RequestBody Person person) {
    LOGGER.warn("Creating person: {}", person);
    return SentryReactiveWebHelper.captureWithRequestHub(
            hub -> {
              hub.captureMessage("User Created!");
            })
        .thenReturn(person);
  }
}
