package io.sentry.spring.jakarta.webflux;

import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.Sentry;
import java.util.function.Function;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Hook meant to used with {@link reactor.core.scheduler.Schedulers#onScheduleHook(String,
 * Function)} to configure Reactor to copy correct scopes into the operating thread.
 */
@ApiStatus.Experimental
public final class SentryScheduleHook implements Function<Runnable, Runnable> {
  @Override
  public Runnable apply(final @NotNull Runnable runnable) {
    final IScopes newScopes = Sentry.getCurrentScopes().forkedCurrentScope("spring.scheduleHook");

    return () -> {
      try (final @NotNull ISentryLifecycleToken ignored = newScopes.makeCurrent()) {
        runnable.run();
      }
    };
  }
}
