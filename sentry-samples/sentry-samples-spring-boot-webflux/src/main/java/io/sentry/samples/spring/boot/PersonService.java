package io.sentry.samples.spring.boot;

import static io.sentry.spring.webflux.SentryWebfluxHubHolder.withSentryOnNext;

import io.sentry.Sentry;
import java.time.Duration;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class PersonService {

  Mono<Person> create(Person person) {
    return Mono.delay(Duration.ofMillis(100))
        .publishOn(Schedulers.boundedElastic())
        .doOnEach(withSentryOnNext(__ -> Sentry.captureMessage("Creating person")))
        //        .doOnNext(__ -> Sentry.captureMessage("Creating person"))
        .map(__ -> person);
  }

  Mono<Person> find(Long id) {
    return Mono.delay(Duration.ofMillis(100))
        .publishOn(Schedulers.boundedElastic())
        .doOnEach(withSentryOnNext(__ -> Sentry.captureMessage("Finding person")))
        .flatMap(
            p -> {
              if (id > 10) {
                return Mono.<Person>error(
                    new RuntimeException(
                        "Caused on purpose for webflux " + Thread.currentThread().getName()));
              } else {
                return Mono.<Person>just(new Person("first", "last"));
              }
            });
  }

  Flux<Person> findAll() {
    return Mono.delay(Duration.ofMillis(100))
        .flux()
        .publishOn(Schedulers.boundedElastic())
        .doOnEach(withSentryOnNext(__ -> Sentry.captureMessage("Finding all people")))
        .flatMap(
            __ -> Flux.<Person>just(new Person("first1", "last1"), new Person("first2", "last2")));
  }

  Flux<Person> findAllException() {
    return Mono.delay(Duration.ofMillis(100))
        .flux()
        .publishOn(Schedulers.boundedElastic())
        .doOnEach(withSentryOnNext(__ -> Sentry.captureMessage("Found another person")))
        .flatMap(
            __ ->
                Flux.<Person>error(new RuntimeException("Caused on purpose for webflux findAll")));
  }
}
