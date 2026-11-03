package io.sentry.spring.boot.integrationtest

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
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
  private val okHttpClient: OkHttpClient,
  private val objectMapper: ObjectMapper,
) {
  @GetMapping("/todo/{id}")
  open fun todo(@PathVariable("id") id: Long): Todo? =
    restTemplate.getForObject(todoUrl(id), Todo::class.java)

  @GetMapping("/todo-webclient/{id}")
  open fun todoWebClient(@PathVariable("id") id: Long): Todo? =
    webClient.get().uri(todoUrl(id)).retrieve().bodyToMono(Todo::class.java).block()

  @GetMapping("/todo-okhttp/{id}")
  open fun todoOkHttp(@PathVariable("id") id: Long): Todo =
    okHttpClient.newCall(Request.Builder().url(todoUrl(id)).build()).execute().use { response ->
      objectMapper.readValue(response.body!!.byteStream(), Todo::class.java)
    }

  private fun todoUrl(id: Long): String = selfUrl("/stub/todos/$id")
}

/** Stands in for the public API the sample fetches todos from, so tests don't need the internet. */
@RestController
open class TodoStubController {
  @GetMapping("/stub/todos/{id}")
  open fun todo(@PathVariable("id") id: Long): Todo = Todo(id, "delectus aut autem")
}
