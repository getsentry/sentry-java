package io.sentry.samples.spring.boot;

import static io.sentry.spring.webflux.SentryReactor.withSentry;

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
        .doOnEach(withSentry(__ -> Sentry.captureMessage("Creating person")))
        .map(__ -> person);
  }
}
