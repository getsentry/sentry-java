package io.sentry.samples.spring.boot;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * {@link SentrySpan} can be added either on the class or the method to create spans around method
 * executions.
 */
@Service
public class PersonService {
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonService.class);

  Mono<Person> create(Person person) {
    return Mono.delay(Duration.ofMillis(100))
        .publishOn(Schedulers.boundedElastic())
        .doOnNext(__ -> LOGGER.warn("Creating person: {}", person))
        .map(__ -> person);
  }
}
