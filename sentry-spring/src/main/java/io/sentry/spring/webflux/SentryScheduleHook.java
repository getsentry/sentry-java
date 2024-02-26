package io.sentry.spring.webflux;

import io.sentry.IHub;
import io.sentry.Sentry;
import java.util.function.Function;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Hook meant to used with {@link reactor.core.scheduler.Schedulers#onScheduleHook(String,
 * Function)} to configure Reactor to copy correct hub into the operating thread.
 */
@ApiStatus.Experimental
public final class SentryScheduleHook implements Function<Runnable, Runnable> {
  @Override
  public Runnable apply(final @NotNull Runnable runnable) {
    final IHub newHub = Sentry.getCurrentHub().clone();

    return () -> {
      final IHub oldState = Sentry.getCurrentHub();
      Sentry.setCurrentHub(newHub);
      try {
        runnable.run();
      } finally {
        Sentry.setCurrentHub(oldState);
      }
    };
  }
}
