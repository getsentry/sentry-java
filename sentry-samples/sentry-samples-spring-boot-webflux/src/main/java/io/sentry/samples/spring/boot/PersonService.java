package io.sentry.samples.spring.boot;

import io.sentry.Sentry;
import java.time.Duration;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PersonService {

  Mono<Person> create(Person person) {
    return Mono.delay(Duration.ofMillis(100))
        .doOnNext(__ -> Sentry.captureMessage("Creating person"))
        .map(__ -> person);
  }
}
