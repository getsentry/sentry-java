package io.sentry.spring.boot4.agentfixture

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient

/** Forwards to this app's own Person endpoints, so a trace continues through outgoing requests. */
@RestController
@RequestMapping("/tracing/")
class DistributedTracingController(private val restClient: RestClient) {
  @GetMapping("{id}")
  fun person(@PathVariable("id") id: Long): Person? =
    restClient.get().uri(selfUrl("/person/$id")).retrieve().body(Person::class.java)

  @PostMapping
  fun create(@RequestBody person: Person): Person? =
    restClient.post().uri(selfUrl("/person/")).body(person).retrieve().body(Person::class.java)
}
