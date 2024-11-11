package io.sentry.samples.spring.boot.jakarta;

import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.spring.jakarta.tracing.SentrySpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * {@link SentrySpan} can be added either on the class or the method to create spans around method
 * executions.
 */
@Service
@SentrySpan
public class PersonService {
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonService.class);

  private final JdbcTemplate jdbcTemplate;
  private int createCount = 0;

  public PersonService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  Person create(Person person) {
    createCount++;
    final ISpan span = Sentry.getSpan();
    if (span != null) {
      span.setMeasurement("create_count", createCount);
    }

    jdbcTemplate.update(
        "insert into person (firstName, lastName) values (?, ?)",
        person.getFirstName(),
        person.getLastName());

    return person;
  }
}
