package io.sentry.samples.spring.boot

import io.sentry.Sentry
import io.sentry.spring.jakarta.tracing.SentrySpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
@SentrySpan
class PersonService(val jdbcTemplate: JdbcTemplate) {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(PersonService::class.java)
    }

    var createCount: Int = 0

    fun create(person: Person): Person {
        createCount++
        Sentry.getSpan()?.let { it.setMeasurement("create_count", createCount) }
        jdbcTemplate.update(
            "insert into (firstName, lastName) values (?, ?)",
            person.firstName,
            person.lastName
        )
        return person
    }
}
