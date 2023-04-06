package io.sentry.samples.spring.boot

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/person/")
class PersonController(val personService: PersonService) {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(PersonController::class.java)
    }

    @GetMapping("{id}")
    fun person(@PathVariable id: Long): Person {
        LOGGER.info("Loading person with id={}", id)
        throw IllegalArgumentException("Something went wrong [id=$id]")
    }

    @PostMapping
    fun create(@RequestBody person: Person): Person {
        return personService.create(person)
    }
}

data class Person(val firstName: String, val lastName: String)
