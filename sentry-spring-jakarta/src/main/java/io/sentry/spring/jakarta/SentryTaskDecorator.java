package io.sentry.spring.jakarta;

import io.sentry.IScopes;
import io.sentry.Sentry;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.Async;

/**
 * Sets a current scopes on a thread running a {@link Runnable} given by parameter. Used to
 * propagate the current {@link IScopes} on the thread executing async task - like MVC controller
 * methods returning a {@link Callable} or Spring beans methods annotated with {@link Async}.
 */
public final class SentryTaskDecorator implements TaskDecorator {
  @Override
  @SuppressWarnings("deprecation")
  public @NotNull Runnable decorate(final @NotNull Runnable runnable) {
    // TODO fork
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
