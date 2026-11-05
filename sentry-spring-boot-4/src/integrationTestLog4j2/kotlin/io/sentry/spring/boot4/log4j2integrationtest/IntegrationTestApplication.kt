package io.sentry.spring.boot4.log4j2integrationtest

import io.sentry.Sentry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication class IntegrationTestApplication

@RestController
@RequestMapping("/person/")
class PersonController {
  private val logger = LoggerFactory.getLogger(PersonController::class.java)

  /** Logs an error through SLF4J, which Log4j2 handles in this app, then fails the request. */
  @GetMapping("{id}")
  fun person(@PathVariable("id") id: Long): String {
    Sentry.addFeatureFlag("my-feature-flag", true)
    logger.error("Trying person with id={}", id, RuntimeException("error while loading"))
    throw IllegalArgumentException("Something went wrong [id=$id]")
  }
}
