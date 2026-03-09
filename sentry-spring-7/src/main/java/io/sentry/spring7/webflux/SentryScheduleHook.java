package io.sentry.spring7.webflux;

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

  /**
   * Runnable class names that should be excluded from scope forking. These are typically internal
   * scheduler loops that reschedule themselves indefinitely, which would cause memory issues due to
   * scope parent chain buildup.
   *
   * @see <a href="https://github.com/getsentry/sentry-java/issues/5051">GitHub Issue #5051</a>
   */
  private static final String[] EXCLUDED_RUNNABLE_PREFIXES = {
    "reactor.kafka.receiver.internals.ConsumerEventLoop"
  };

  @Override
  public Runnable apply(final @NotNull Runnable runnable) {
    final String runnableClassName = runnable.getClass().getName();
    for (final String excludedPrefix : EXCLUDED_RUNNABLE_PREFIXES) {
      if (runnableClassName.startsWith(excludedPrefix)) {
        return runnable;
      }
    }

    final IScopes newScopes = Sentry.getCurrentScopes().forkedCurrentScope("spring.scheduleHook");

    return () -> {
      try (final @NotNull ISentryLifecycleToken ignored = newScopes.makeCurrent()) {
        runnable.run();
      }
    };
  }
}
