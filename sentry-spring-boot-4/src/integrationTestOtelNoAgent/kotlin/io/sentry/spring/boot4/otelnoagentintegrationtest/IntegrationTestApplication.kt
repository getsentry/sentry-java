package io.sentry.spring.boot4.otelnoagentintegrationtest

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.instrumentation.annotations.WithSpan
import io.sentry.Sentry
import io.sentry.spring7.tracing.SentrySpan
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@SpringBootApplication
class IntegrationTestApplication {
  // Built from Spring Boot's builders, which Sentry customizes to trace outgoing requests.
  @Bean fun restTemplate(builder: RestTemplateBuilder): RestTemplate = builder.build()

  @Bean fun restClient(builder: RestClient.Builder): RestClient = builder.build()

  @Bean fun webClient(builder: WebClient.Builder): WebClient = builder.build()

  @Bean
  fun tracer(openTelemetry: OpenTelemetry): Tracer = openTelemetry.getTracer("integration-test")
}

/**
 * The URL of [path] on this app, which listens on a random port. Only works on a request thread,
 * because it reads the address from the current request.
 */
fun selfUrl(path: String): String =
  ServletUriComponentsBuilder.fromCurrentContextPath().path(path).toUriString()

class Person(var firstName: String? = null, var lastName: String? = null)

@RestController
@RequestMapping("/person/")
class PersonController(private val personService: PersonService, private val tracer: Tracer) {
  private val logger = LoggerFactory.getLogger(PersonController::class.java)

  /**
   * Nests a span from an OpenTelemetry annotation, one from the OpenTelemetry API and one from the
   * Sentry API, each with its own feature flags, then fails the request.
   */
  @GetMapping("{id}")
  @WithSpan("personSpanThroughOtelAnnotation")
  fun person(@PathVariable("id") id: Long): Person {
    Sentry.addFeatureFlag("outer-feature-flag", true)
    val span = tracer.spanBuilder("spanCreatedThroughOtelApi").startSpan()
    try {
      span.makeCurrent().use {
        Sentry.setAttribute("user.type", "admin")
        Sentry.setAttribute("feature.version", 2)
        Sentry.setAttribute("debug.enabled", true)
        Sentry.logger().warn("warn Sentry logging")
        Sentry.logger().error("error Sentry logging")
        Sentry.logger().info("hello %s %s", "there", "world!")
        Sentry.addFeatureFlag("inner-feature-flag", true)
        val sentrySpan = Sentry.getSpan()!!.startChild("spanCreatedThroughSentryApi")
        try {
          logger.error("Trying person with id={}", id, RuntimeException("error while loading"))
          throw IllegalArgumentException("Something went wrong [id=$id]")
        } finally {
          sentrySpan.finish()
        }
      }
    } finally {
      span.end()
    }
  }

  @PostMapping
  fun create(@RequestBody person: Person): Person {
    val span = tracer.spanBuilder("spanCreatedThroughOtelApi").startSpan()
    try {
      span.makeCurrent().use {
        val sentrySpan = Sentry.getSpan()!!.startChild("spanCreatedThroughSentryApi")
        try {
          return personService.create(person)
        } finally {
          sentrySpan.finish()
        }
      }
    } finally {
      span.end()
    }
  }
}

@Service
@SentrySpan
class PersonService(private val jdbcTemplate: JdbcTemplate) {
  fun create(person: Person): Person {
    jdbcTemplate.update(
      "insert into person (firstName, lastName) values (?, ?)",
      person.firstName,
      person.lastName,
    )
    return person
  }
}
