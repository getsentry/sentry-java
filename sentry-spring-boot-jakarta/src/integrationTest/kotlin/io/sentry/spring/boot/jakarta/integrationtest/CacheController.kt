package io.sentry.spring.boot.jakarta.integrationtest

import java.util.concurrent.ConcurrentHashMap
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/cache/")
open class CacheController(private val todoCache: TodoCache) {
  @GetMapping("{id}") open fun get(@PathVariable("id") id: Long): Todo? = todoCache.get(id)

  @PostMapping open fun save(@RequestBody todo: Todo): Todo = todoCache.save(todo)

  @DeleteMapping("{id}") open fun delete(@PathVariable("id") id: Long) = todoCache.delete(id)
}

/** Reads and writes todos through Spring's cache abstraction, which Sentry traces. */
@Service
open class TodoCache {
  private val store = ConcurrentHashMap<Long, Todo>()

  @Cacheable(value = ["todos"], key = "#id") open fun get(id: Long): Todo? = store[id]

  @CachePut(value = ["todos"], key = "#todo.id")
  open fun save(todo: Todo): Todo {
    store[todo.id!!] = todo
    return todo
  }

  @CacheEvict(value = ["todos"], key = "#id")
  open fun delete(id: Long) {
    store.remove(id)
  }
}
