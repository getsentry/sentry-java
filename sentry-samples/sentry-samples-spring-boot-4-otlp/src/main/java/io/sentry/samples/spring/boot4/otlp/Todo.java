package io.sentry.samples.spring.boot4.otlp;

public class Todo {
  private final Long id;
  private final String title;
  private final boolean completed;

  public Todo(Long id, String title, boolean completed) {
    this.id = id;
    this.title = title;
    this.completed = completed;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public boolean isCompleted() {
    return completed;
  }
}
