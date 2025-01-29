package io.sentry.samples.spring.boot.jakarta;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.sentry.ISpan;
import io.sentry.Sentry;
import org.jetbrains.annotations.NotNull;
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
  private final Tracer tracer;
  private final ApiService apiService;
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonController.class);

  public PersonController(PersonService personService, Tracer tracer, ApiService apiService) {
    this.personService = personService;
    this.tracer = tracer;
    this.apiService = apiService;
  }

  @GetMapping("{id}")
  Person person(@PathVariable Long id) {
    Span span = tracer.spanBuilder("spanCreatedThroughOtelApi").startSpan();
    try (final @NotNull Scope spanScope = span.makeCurrent()) {
      ISpan currentSpan = Sentry.getSpan();
      ISpan sentrySpan = currentSpan.startChild("spanCreatedThroughSentryApi");
      try {
        apiService.apiRequest(id.toString());
        LOGGER.error("Trying person with id={}", id, new RuntimeException("error while loading"));
        throw new IllegalArgumentException("Something went wrong [id=" + id + "]");
      } finally {
        sentrySpan.finish();
      }
    } finally {
      span.end();
    }
  }

  @PostMapping
  Person create(@RequestBody Person person) {
    Span span = tracer.spanBuilder("spanCreatedThroughOtelApi").startSpan();
    try (final @NotNull Scope spanScope = span.makeCurrent()) {
      ISpan sentrySpan = Sentry.getSpan().startChild("spanCreatedThroughSentryApi");
      try {
        return personService.create(person);
      } finally {
        sentrySpan.finish();
      }
    } finally {
      span.end();
    }
  }
}
