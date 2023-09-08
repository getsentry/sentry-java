package io.sentry.samples.spring.boot.jakarta.graphql;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
public class GreetingController {

  @QueryMapping
  public Mono<String> greeting(final @Argument String name) {
    if ("crash".equalsIgnoreCase(name)) {
      //      return Mono.error(new RuntimeException("causing an error for " + name));
      throw new RuntimeException("causing an error for " + name);
    }
    return Mono.just("Hello " + name + "!");
  }
}
