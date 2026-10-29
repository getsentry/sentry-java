package io.sentry.spring.boot.agentfixture

import io.opentelemetry.api.trace.Tracer
import io.sentry.Sentry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient

open class Todo(var id: Long? = null, var title: String? = null, var completed: Boolean = false)

/** Fetches a todo through each HTTP client that Sentry traces. */
@RestController
open class TodoController(
  private val restTemplate: RestTemplate,
  private val webClient: WebClient,
  private val tracer: Tracer,
) {
  @GetMapping("/todo/{id}")
  open fun todo(@PathVariable("id") id: Long): Todo? =
    inSpans("todoSpanOtelApi", "todoSpanSentryApi") {
      restTemplate.getForObject(todoUrl(id), Todo::class.java)
    }

  @GetMapping("/todo-webclient/{id}")
  open fun todoWebClient(@PathVariable("id") id: Long): Todo? =
    webClient.get().uri(todoUrl(id)).retrieve().bodyToMono(Todo::class.java).block()

  /** Runs [block] in a span from the OpenTelemetry API and, inside it, one from the Sentry API. */
  private fun <T> inSpans(otelSpanName: String, sentrySpanOp: String, block: () -> T): T {
    val otelSpan = tracer.spanBuilder(otelSpanName).startSpan()
    try {
      otelSpan.makeCurrent().use {
        val sentrySpan = Sentry.getSpan()!!.startChild(sentrySpanOp)
        try {
          return block()
        } finally {
          sentrySpan.finish()
        }
      }
    } finally {
      otelSpan.end()
    }
  }

  private fun todoUrl(id: Long): String = selfUrl("/stub/todos/$id")
}

/** Stands in for the public API the sample fetches todos from, so tests don't need the internet. */
@RestController
open class TodoStubController {
  @GetMapping("/stub/todos/{id}")
  open fun todo(@PathVariable("id") id: Long): Todo = Todo(id, "delectus aut autem")
}
