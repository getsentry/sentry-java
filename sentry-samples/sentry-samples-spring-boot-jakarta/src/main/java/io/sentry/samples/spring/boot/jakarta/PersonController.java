package io.sentry.samples.spring.boot.jakarta;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.sentry.Sentry;
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
  @SuppressWarnings("try")
  Person person(@PathVariable Long id) {
    try {
      throw new Exception("This is a test.");
    } catch (Exception e) {
      Sentry.captureException(e);
    }

    Context oldContext = Context.current();

    Tracer tracer =
        GlobalOpenTelemetry.getTracer(
            "instrumentation-scope-name", "instrumentation-scope-version");
    SpanBuilder spanBuilder = tracer.spanBuilder("span-builder-name");
    Span span = spanBuilder.startSpan();
    try (Scope scope = span.makeCurrent()) {
      //      Span.current().setAttribute("sentry-test-attr", "sentry-test-attr-val").makeCurrent();
      Context modifiedContext =
          Context.current().with(ContextKey.named("sentry.context.test"), new Person("fN", "lN"));
      // do something else
      try (Scope scope2 = modifiedContext.makeCurrent()) {
        System.out.println(modifiedContext);
      }
    }
    span.end();

    System.out.println(oldContext);

    LOGGER.error("Trying person with id={}", id, new RuntimeException("error while loading"));
    throw new IllegalArgumentException("Something went wrong [id=" + id + "]");
  }

  @PostMapping
  Person create(@RequestBody Person person) {
    return personService.create(person);
  }
}
