package io.sentry.samples.spring.boot4.otlp;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cache/")
public class CacheController {
  private final TodoService todoService;

  public CacheController(TodoService todoService) {
    this.todoService = todoService;
  }

  @GetMapping("{id}")
  Todo get(@PathVariable Long id) {
    return todoService.get(id);
  }

  @PostMapping
  Todo save(@RequestBody Todo todo) {
    return todoService.save(todo);
  }

  @DeleteMapping("{id}")
  void delete(@PathVariable Long id) {
    todoService.delete(id);
  }
}
