package io.sentry.samples.spring.boot.jakarta;

import io.sentry.Sentry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;


public class ProfilingInitializer implements ApplicationListener<ApplicationEvent> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProfilingInitializer.class);


  //  @Override
  //  public boolean supportsEventType(final @NotNull ResolvableType eventType) {
  //    return true;
  //  }

  @Override
  public void onApplicationEvent(final @NotNull ApplicationEvent event) {
    if (event instanceof ContextRefreshedEvent) {
      Sentry.startProfiler();
    }
  }
}
