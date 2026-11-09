package io.sentry.spring.boot.jakarta.webfluxintegrationtest

import io.sentry.Sentry
import java.time.Duration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

// Classes and methods are open because Spring proxies them and this module doesn't apply the
// kotlin-spring plugin.

@SpringBootApplication
@EnableCaching
open class WebfluxIntegrationTestApplication {
  // Built from Spring Boot's builder, which Sentry customizes to trace outgoing requests.
  @Bean open fun webClient(builder: WebClient.Builder): WebClient = builder.build()
}

/** The URL of [path] on this app, which listens on a random port, based on [request]. */
fun selfUrl(request: ServerHttpRequest, path: String): String =
  UriComponentsBuilder.fromUri(request.uri).replacePath(path).replaceQuery(null).toUriString()

open class Person(var firstName: String? = null, var lastName: String? = null)

open class Todo(var id: Long? = null, var title: String? = null, var completed: Boolean = false)

@RestController
@RequestMapping("/person/")
open class PersonController {
  /** Reports to Sentry through every API an app uses, then fails the request. */
  @GetMapping("{id}")
  open fun person(@PathVariable("id") id: Long): Person {
    Sentry.setAttribute("user.type", "admin")
    Sentry.setAttribute("feature.version", 2)
    Sentry.setAttribute("debug.enabled", true)
    Sentry.logger().warn("warn Sentry logging")
    Sentry.logger().error("error Sentry logging")
    Sentry.logger().info("hello %s %s", "there", "world!")
    Sentry.addFeatureFlag("my-feature-flag", true)
    throw IllegalArgumentException("Something went wrong [id=$id]")
  }

  /** Finishes on another thread, which the Sentry scope has to follow through Reactor. */
  @PostMapping
  open fun create(@RequestBody person: Person): Mono<Person> =
    Mono.delay(Duration.ofMillis(100))
      .publishOn(Schedulers.boundedElastic())
      .doOnNext { Sentry.captureMessage("Creating person") }
      .map { person }
}

/** Forwards to this app's own Person endpoints, so a trace continues through outgoing requests. */
@RestController
@RequestMapping("/tracing/")
open class DistributedTracingController(private val webClient: WebClient) {
  @GetMapping("{id}")
  open fun person(@PathVariable("id") id: Long, request: ServerHttpRequest): Mono<Person> =
    webClient.get().uri(selfUrl(request, "/person/$id")).retrieve().bodyToMono(Person::class.java)

  @PostMapping
  open fun create(@RequestBody person: Person, request: ServerHttpRequest): Mono<Person> =
    webClient
      .post()
      .uri(selfUrl(request, "/person/"))
      .bodyValue(person)
      .retrieve()
      .bodyToMono(Person::class.java)
}

@RestController
open class TodoController(private val webClient: WebClient) {
  @GetMapping("/todo-webclient/{id}")
  open fun todo(@PathVariable("id") id: Long, request: ServerHttpRequest): Mono<Todo> =
    webClient.get().uri(selfUrl(request, "/stub/todos/$id")).retrieve().bodyToMono(Todo::class.java)

  /**
   * Stands in for the public API the sample fetches todos from, so tests don't need the internet.
   */
  @GetMapping("/stub/todos/{id}")
  open fun stub(@PathVariable("id") id: Long): Todo = Todo(id, "delectus aut autem")
}

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
