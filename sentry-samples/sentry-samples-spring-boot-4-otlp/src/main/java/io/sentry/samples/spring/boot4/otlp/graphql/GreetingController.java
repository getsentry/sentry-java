package io.sentry.samples.spring.boot4.otlp.graphql;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GreetingController {

  @QueryMapping
  public String greeting(final @Argument String name) {
    if ("crash".equalsIgnoreCase(name)) {
      throw new RuntimeException("causing an error for " + name);
    }
    return "Hello " + name + "!";
  }
}
