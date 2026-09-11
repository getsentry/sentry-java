package io.sentry.samples.spring.boot4;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class TodoService {
  private final Map<Long, Todo> store = new ConcurrentHashMap<>();

  @Cacheable(value = "todos", key = "#id")
  public Todo get(Long id) {
    return store.get(id);
  }

  @CachePut(value = "todos", key = "#todo.id")
  public Todo save(Todo todo) {
    store.put(todo.getId(), todo);
    return todo;
  }

  @CacheEvict(value = "todos", key = "#id")
  public void delete(Long id) {
    store.remove(id);
  }
}
