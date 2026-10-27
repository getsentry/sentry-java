package io.sentry.spring.boot4.otlpintegrationtest

import io.sentry.reactor.SentryReactorUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

class Todo(var id: Long? = null, var title: String? = null, var completed: Boolean = false)

/** Fetches a todo through each HTTP client that Sentry traces. */
@RestController
class TodoController(
  private val restTemplate: RestTemplate,
  private val webClient: WebClient,
  private val restClient: RestClient,
) {
  @GetMapping("/todo/{id}")
  fun todo(@PathVariable("id") id: Long): Todo? =
    restTemplate.getForObject(todoUrl(id), Todo::class.java)

  /** Requests from another thread, which the Sentry scope has to follow through Reactor. */
  @GetMapping("/todo-webclient/{id}")
  fun todoWebClient(@PathVariable("id") id: Long): Todo? {
    val url = todoUrl(id)
    Hooks.enableAutomaticContextPropagation()
    return SentryReactorUtils.withSentry(
        Mono.just(true).publishOn(Schedulers.boundedElastic()).flatMap {
          webClient.get().uri(url).retrieve().bodyToMono(Todo::class.java)
        }
      )
      .block()
  }

  @GetMapping("/todo-restclient/{id}")
  fun todoRestClient(@PathVariable("id") id: Long): Todo? =
    restClient.get().uri(todoUrl(id)).retrieve().body(Todo::class.java)

  private fun todoUrl(id: Long): String = selfUrl("/stub/todos/$id")
}

/** Stands in for the public API the sample fetches todos from, so tests don't need the internet. */
@RestController
class TodoStubController {
  @GetMapping("/stub/todos/{id}")
  fun todo(@PathVariable("id") id: Long): Todo = Todo(id, "delectus aut autem")
}
