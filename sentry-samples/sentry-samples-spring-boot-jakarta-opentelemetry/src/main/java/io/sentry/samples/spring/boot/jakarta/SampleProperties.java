package io.sentry.samples.spring.boot.jakarta;

import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("sample")
public class SampleProperties {
  private @Nullable String todoUrl;

  @Nullable
  public String getTodoUrl() {
    return todoUrl;
  }

  public void setTodoUrl(@Nullable String todoUrl) {
    this.todoUrl = todoUrl;
  }
}
