package io.sentry.spring.webflux;

import io.sentry.IHub;
import io.sentry.Sentry;
import java.util.function.Function;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hook meant to used with {@link reactor.core.scheduler.Schedulers#onScheduleHook(String,
 * Function)} to configure Reactor to copy correct hub into the operating thread.
 */
@ApiStatus.Experimental
public final class SentryScheduleHook implements Function<Runnable, Runnable> {

  private static final Logger log = LoggerFactory.getLogger("hooklogger");

  @Override
  public Runnable apply(final @NotNull Runnable runnable) {
    String threadName = Thread.currentThread().getName();
    log.debug("Starting hook on " + threadName);
    final IHub oldState = Sentry.getCurrentHub();
    final IHub newHub = Sentry.getCurrentHub().clone();
    return () -> {
      Sentry.setCurrentHub(newHub);
      try {
        runnable.run();
      } finally {
        Sentry.setCurrentHub(oldState);
        log.debug("Ended hook on " + threadName);
      }
    };
  }
}
