package io.sentry.samples.spring.boot;

import io.sentry.spring.tracing.SentrySpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PersonService {
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonService.class);

  private final JdbcTemplate jdbcTemplate;

  public PersonService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @SentrySpan
  Person create(Person person) {
    LOGGER.warn("Creating person: {}", person);
    jdbcTemplate.update(
        "insert into person (firstName, lastName) values (?, ?)",
        person.getFirstName(),
        person.getLastName());
    return person;
  }
}
