package io.sentry.spring.boot.jakarta.agentfixture

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.sentry.Sentry
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.web.context.WebServerPortFileWriter
import org.springframework.context.annotation.Bean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
open class AgentFixtureApplication {
  @Bean open fun tracer(): Tracer = GlobalOpenTelemetry.get().getTracer("agent-fixture")
}

fun main(args: Array<String>) {
  SpringApplication(AgentFixtureApplication::class.java)
    .apply { addListeners(WebServerPortFileWriter()) }
    .run(*args)
}

/** Reports to Sentry through every API an app with the agent uses, then fails the request. */
@RestController
open class PersonController(private val tracer: Tracer) {
  private val logger = LoggerFactory.getLogger(PersonController::class.java)

  @GetMapping("/person/{id}")
  open fun person(@PathVariable("id") id: Long): String {
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
}
