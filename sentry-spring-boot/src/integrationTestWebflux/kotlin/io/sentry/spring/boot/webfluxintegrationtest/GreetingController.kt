package io.sentry.spring.boot.webfluxintegrationtest

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

/** Throws when given "crash", so tests can check error reporting. */
@Controller
open class GreetingController {
  @QueryMapping
  open fun greeting(@Argument name: String): String {
    if (name == "crash") {
      throw RuntimeException("causing an error for $name")
    }
    return "Hello $name!"
  }
}
