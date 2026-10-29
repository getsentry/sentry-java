package io.sentry.spring.boot.webfluxintegrationtest

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono

/** Throws when given "crash", so tests can check error reporting. */
@Controller
open class GreetingController {
  // Returns a Mono, as a reactive app would, so the test covers Sentry finishing the field's span
  // asynchronously.
  @QueryMapping
  open fun greeting(@Argument name: String): Mono<String> {
    if (name == "crash") {
      throw RuntimeException("causing an error for $name")
    }
    return Mono.just("Hello $name!")
  }
}
