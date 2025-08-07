package io.sentry.samples.spring.boot4;

import io.sentry.Sentry;
import java.time.Duration;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class PersonService {

  Mono<Person> create(Person person) {
    return Mono.delay(Duration.ofMillis(100))
        .publishOn(Schedulers.boundedElastic())
        .doOnNext(
            __ -> {
              Sentry.captureMessage("Creating person");
            })
        .map(__ -> person);
  }
}
