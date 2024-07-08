package io.sentry.samples.spring.boot.jakarta;

import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.SpanStatus;
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
  private final AsyncService asyncService;
  private final ApiService apiService;
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonController.class);

  public PersonController(
      PersonService personService, AsyncService asyncService, ApiService apiService) {
    this.personService = personService;
    this.asyncService = asyncService;
    this.apiService = apiService;
  }

  @GetMapping("{id}")
  Person person(@PathVariable Long id) {
    System.out.println("before Sentry.getSpan()");
    ISpan span = Sentry.getSpan();
    System.out.println("after Sentry.getSpan()");
    LOGGER.error("Trying person with id={}", id, new RuntimeException("error while loading"));
    System.out.println("before Sentry.getSpan()");
    ISpan childSpan = span.startChild("testop-potel-1");
    System.out.println("after Sentry.getSpan()");
    childSpan.setTag("potel-tag-1", "tag-val-1");
    childSpan.setData("potel-data-1", "data-val-1");
    childSpan.setMeasurement("potel-measure-1", 1.99);
    childSpan.setStatus(SpanStatus.OUT_OF_RANGE);
    System.out.println("after modify childspan");
    try { // (final @NotNull ISentryLifecycleToken token = childSpan.makeCurrent()) {
      //      Span otelSpan =
      //          GlobalOpenTelemetry.getTracer("customer-tracer")
      //              .spanBuilder("customer-spanbuilder")
      //              .startSpan();
      //      try (Scope scope = otelSpan.makeCurrent()) {
      //        asyncService.doAsync();
      apiService.apiRequest("abc");
      throw new IllegalArgumentException("Something went wrong [id=" + id + "]");
      //      } finally {
      //        otelSpan.end();
      //      }
    } finally {
      childSpan.finish();
    }
  }

  @PostMapping
  Person create(@RequestBody Person person) {
    return personService.create(person);
  }
}
