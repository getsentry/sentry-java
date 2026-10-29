package io.sentry.spring.boot.otelnoagentintegrationtest

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate

/** Forwards to this app's own Person endpoints, so a trace continues through outgoing requests. */
@RestController
@RequestMapping("/tracing/")
open class DistributedTracingController(private val restTemplate: RestTemplate) {
  @GetMapping("{id}")
  open fun person(@PathVariable("id") id: Long): Person? =
    restTemplate.getForObject(selfUrl("/person/$id"), Person::class.java)

  @PostMapping
  open fun create(@RequestBody person: Person): Person? =
    restTemplate.postForObject(selfUrl("/person/"), person, Person::class.java)
}
