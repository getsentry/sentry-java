package io.sentry.samples.spring.boot4;

import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.SentryRuntime;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringBootVersion;
import org.springframework.stereotype.Component;

/**
 * Custom {@link EventProcessor} implementation lets modifying {@link SentryEvent}s before they are
 * sent to Sentry.
 */
@Component
public class CustomEventProcessor implements EventProcessor {
  private final String springBootVersion;

  public CustomEventProcessor(String springBootVersion) {
    this.springBootVersion = springBootVersion;
  }

  public CustomEventProcessor() {
    this(SpringBootVersion.getVersion());
  }

  @Override
  public @NotNull SentryEvent process(@NotNull SentryEvent event, @NotNull Hint hint) {
    final SentryRuntime runtime = new SentryRuntime();
    runtime.setVersion(springBootVersion);
    runtime.setName("Spring Boot");
    event.getContexts().setRuntime(runtime);
    return event;
  }
}
