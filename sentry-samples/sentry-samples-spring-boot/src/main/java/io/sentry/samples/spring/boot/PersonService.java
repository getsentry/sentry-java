package io.sentry.samples.spring.boot;

import io.sentry.spring.tracing.SentrySpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PersonService {
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonService.class);

  @SentrySpan
  Person create(Person person) {
    LOGGER.warn("Creating person: {}", person);
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
    }
    return person;
  }
}
