package io.sentry.spring.webflux;

import io.sentry.IScopes;
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
  @SuppressWarnings("deprecation")
  public Runnable apply(final @NotNull Runnable runnable) {
    // TODO fork instead
    final IScopes newHub = Sentry.getCurrentScopes().clone();

    return () -> {
      final IScopes oldState = Sentry.getCurrentScopes();
      Sentry.setCurrentScopes(newHub);
      try {
        runnable.run();
      } finally {
        Sentry.setCurrentScopes(oldState);
      }
    };
  }
}
