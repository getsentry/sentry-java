package io.sentry.samples.spring.boot;

import io.sentry.EventProcessor;
import io.sentry.SentryEvent;
import io.sentry.protocol.SentryRuntime;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Custom {@link EventProcessor} implementation lets modifying {@link SentryEvent}s before they are
 * sent to Sentry.
 */
@Component
public class CustomEventProcessor implements EventProcessor {
  private final String javaVersion;
  private final String javaVendor;

  public CustomEventProcessor(String javaVersion, String javaVendor) {
    this.javaVersion = javaVersion;
    this.javaVendor = javaVendor;
  }

  public CustomEventProcessor() {
    this(System.getProperty("java.version"), System.getProperty("java.vendor"));
  }

  @Override
  public SentryEvent process(SentryEvent event, @Nullable Object hint) {
    final SentryRuntime runtime = new SentryRuntime();
    runtime.setVersion(javaVersion);
    runtime.setName(javaVendor);
    event.getContexts().setRuntime(runtime);
    return event;
  }
}
