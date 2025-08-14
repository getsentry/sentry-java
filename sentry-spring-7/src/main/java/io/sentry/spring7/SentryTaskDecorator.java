package io.sentry.spring7;

import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.Sentry;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.Async;

/**
 * Forks scopes for a thread running a {@link Runnable} given by parameter. Used to propagate the
 * current {@link IScopes} on the thread executing async task - like MVC controller methods
 * returning a {@link Callable} or Spring beans methods annotated with {@link Async}.
 */
public final class SentryTaskDecorator implements TaskDecorator {
  @Override
  public @NotNull Runnable decorate(final @NotNull Runnable runnable) {
    final IScopes newScopes = Sentry.getCurrentScopes().forkedScopes("SentryTaskDecorator");

    return () -> {
      try (final @NotNull ISentryLifecycleToken ignored = newScopes.makeCurrent()) {
        runnable.run();
      }
    };
  }
}
