package io.sentry.samples.spring.boot;

import static io.sentry.spring.webflux.SentryWebfluxHubHolder.withSentryFinally;
import static io.sentry.spring.webflux.SentryWebfluxHubHolder.withSentryOnComplete;
import static io.sentry.spring.webflux.SentryWebfluxHubHolder.withSentryOnError;
import static io.sentry.spring.webflux.SentryWebfluxHubHolder.withSentryOnFirst;
import static io.sentry.spring.webflux.SentryWebfluxHubHolder.withSentryOnNext;

import io.sentry.spring.webflux.SentryWebfluxHubHolder;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/person/")
public class PersonController {
  private final PersonService personService;
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonController.class);

  public PersonController(PersonService personService) {
    this.personService = personService;
  }

  @GetMapping("p/{id}")
  Person person(@PathVariable Long id) {
    LOGGER.info("Loading person with id={}", id);
    throw new IllegalArgumentException("Something went wrong [id=" + id + "]");
  }

  @GetMapping("p4/{id}")
  Person person4(@PathVariable Long id) {
    return new Person("first", "last");
  }

  @GetMapping("p2/{id}")
  Mono<Person> person2(@PathVariable Long id) {
    return Mono.error(new IllegalArgumentException("Something went wrong2 [id=" + id + "]"));
    //    LOGGER.info("Loading person2 with id={}", id);
  }

  @GetMapping("p3/{id}")
  Mono<Person> person3(@PathVariable Long id, ServerWebExchange serverWebExchange) {
    String uniq = UUID.randomUUID().toString();
    return personService
        .find(id)
        .doFirst(
            withSentryOnFirst(
                serverWebExchange, () -> LOGGER.info("Finding person with id " + uniq)))
        .doOnError(
            withSentryOnError(serverWebExchange, e -> LOGGER.error("Hello from error " + uniq, e)))
        .doFinally(
            withSentryFinally(
                serverWebExchange,
                s -> LOGGER.warn("Finally for person with id " + uniq + ":" + s)))
        .doOnEach(withSentryOnComplete(x -> LOGGER.info("oncomplete " + uniq)))
        .doOnEach(withSentryOnNext(p -> LOGGER.info("Found " + uniq)))
        .doOnEach(withSentryOnError(e -> LOGGER.error("oneach error " + uniq, e)));
  }

  @GetMapping("all1")
  Flux<Person> all(ServerWebExchange serverWebExchange) {
    String uniq = UUID.randomUUID().toString();
    return personService
        .findAll()
        .doFirst(
            withSentryOnFirst(serverWebExchange, () -> LOGGER.info("Finding all people " + uniq)))
        .doOnError(
            withSentryOnError(serverWebExchange, e -> LOGGER.error("Hello from error " + uniq, e)))
        .doFinally(
            withSentryFinally(
                serverWebExchange, __ -> LOGGER.warn("Finally for all people " + uniq)))
        .doOnEach(withSentryOnComplete(__ -> LOGGER.info("oncomplete " + uniq)))
        .doOnEach(withSentryOnNext(p -> LOGGER.info("Found " + uniq + " " + p.toString())))
        .doOnComplete(
            withSentryOnComplete(serverWebExchange, () -> LOGGER.info("on complete " + uniq)))
        .doOnEach(withSentryOnError(e -> LOGGER.error("oneach error " + uniq, e)));
  }

  @GetMapping("allerror")
  Flux<Person> allError(ServerWebExchange serverWebExchange) {
    String uniq = UUID.randomUUID().toString();
    return personService
        .findAllException()
        .doFirst(
            withSentryOnFirst(
                serverWebExchange, () -> LOGGER.info("Finding all people with error " + uniq)))
        .doOnError(
            withSentryOnError(serverWebExchange, e -> LOGGER.error("Hello from error " + uniq, e)))
        .doFinally(
            withSentryFinally(
                serverWebExchange, s -> LOGGER.warn("Finally for all people with error " + uniq)))
        .doOnEach(withSentryOnComplete(x -> LOGGER.info("oncomplete " + uniq)))
        .doOnEach(withSentryOnNext(p -> LOGGER.info("Found " + uniq + " " + p.toString())))
        .doOnComplete(
            withSentryOnComplete(serverWebExchange, () -> LOGGER.info("on complete " + uniq)))
        .doOnEach(withSentryOnError(e -> LOGGER.error("oneach error " + uniq, e)));
  }

  @GetMapping("allerror3")
  Flux<Person> allError3(ServerWebExchange serverWebExchange) {
    String uniq = UUID.randomUUID().toString();
    return personService
        .findAll()
        .doFirst(
            withSentryOnFirst(
                serverWebExchange, () -> LOGGER.info("Finding all people with error 3 " + uniq)))
        .doOnError(
            withSentryOnError(
                serverWebExchange, e -> LOGGER.error("Hello from error 3 " + uniq, e)))
        .doFinally(
            withSentryFinally(
                serverWebExchange,
                s -> LOGGER.error("Finally for all people with error 3 " + uniq)))
        .doOnEach(withSentryOnComplete(x -> LOGGER.info("oncomplete 3 " + uniq)))
        .doOnEach(withSentryOnNext(p -> LOGGER.info("Found 3 " + uniq + " " + p.toString())))
        .doOnComplete(
            withSentryOnComplete(serverWebExchange, () -> LOGGER.info("on complete 3 " + uniq)))
        .doOnEach(withSentryOnError(e -> LOGGER.error("oneach error 3 " + uniq, e)));
  }

  @GetMapping("all")
  Flux<Person> findAll(ServerWebExchange serverWebExchange) {
    return personService
        .findAll()
        .doFirst(withSentryOnFirst(serverWebExchange, () -> LOGGER.info("doFirst")))
        .doOnNext(withSentryOnNext(serverWebExchange, p -> LOGGER.info("doOnNext " + p.toString())))
        .doOnComplete(withSentryOnComplete(serverWebExchange, () -> LOGGER.info("doOnComplete")))
        .doOnError(withSentryOnError(serverWebExchange, e -> LOGGER.error("doOnError", e)))
        .doFinally(withSentryFinally(serverWebExchange, __ -> LOGGER.info("doFinally")))
        .doOnEach(withSentryOnComplete(__ -> LOGGER.info("onEachComplete")))
        .doOnEach(withSentryOnNext(p -> LOGGER.info("onEachNext " + p.toString())))
        .doOnEach(withSentryOnError(e -> LOGGER.error("onEachError", e)))
        .flatMap(
            p ->
                SentryWebfluxHubHolder.getHubFlux()
                    .map(hub -> hub.captureMessage("Hello message"))
                    .flatMap(__ -> Flux.just(p)));
  }

  @PostMapping
  Mono<Person> create(@RequestBody Person person) {
    return personService.create(person);
  }
}
