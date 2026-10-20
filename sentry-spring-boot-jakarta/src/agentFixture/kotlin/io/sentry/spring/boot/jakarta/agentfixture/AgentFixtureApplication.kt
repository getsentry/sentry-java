package io.sentry.spring.boot.jakarta.agentfixture

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.sentry.Sentry
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.boot.web.context.WebServerPortFileWriter
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

// Classes and methods are open because Spring proxies them and this module doesn't apply the
// kotlin-spring plugin.

@SpringBootApplication
open class AgentFixtureApplication {
  @Bean open fun restTemplate(builder: RestTemplateBuilder): RestTemplate = builder.build()

  @Bean open fun restClient(builder: RestClient.Builder): RestClient = builder.build()

  @Bean open fun webClient(builder: WebClient.Builder): WebClient = builder.build()

  // The agent sets up OpenTelemetry, so there is no OpenTelemetry bean to get the tracer from.
  @Bean open fun tracer(): Tracer = GlobalOpenTelemetry.get().getTracer("agent-fixture")
}

fun main(args: Array<String>) {
  SpringApplication(AgentFixtureApplication::class.java)
    .apply { addListeners(WebServerPortFileWriter()) }
    .run(*args)
}

/**
 * The URL of [path] on this app, which listens on a random port. Only works on a request thread,
 * because it reads the address from the current request.
 */
fun selfUrl(path: String): String =
  ServletUriComponentsBuilder.fromCurrentContextPath().path(path).toUriString()

open class Person(var firstName: String? = null, var lastName: String? = null)

@RestController
@RequestMapping("/person/")
open class PersonController(private val personService: PersonService, private val tracer: Tracer) {
  private val logger = LoggerFactory.getLogger(PersonController::class.java)

  /** Reports to Sentry through every API an app with the agent uses, then fails the request. */
  @GetMapping("{id}")
  open fun person(@PathVariable("id") id: Long): Person {
    Sentry.addFeatureFlag("transaction-feature-flag", true)
    val span = tracer.spanBuilder("spanCreatedThroughOtelApi").startSpan()
    try {
      span.makeCurrent().use {
        Sentry.setAttribute("user.type", "admin")
        Sentry.setAttribute("feature.version", 2)
        Sentry.setAttribute("debug.enabled", true)
        Sentry.logger().warn("warn Sentry logging")
        Sentry.logger().error("error Sentry logging")
        Sentry.logger().info("hello %s %s", "there", "world!")
        Sentry.addFeatureFlag("my-feature-flag", true)
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
  open fun create(@RequestBody person: Person): Person {
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
open class PersonService(private val jdbcTemplate: JdbcTemplate) {
  open fun create(person: Person): Person {
    jdbcTemplate.update(
      "insert into person (firstName, lastName) values (?, ?)",
      person.firstName,
      person.lastName,
    )
    return person
  }
}
