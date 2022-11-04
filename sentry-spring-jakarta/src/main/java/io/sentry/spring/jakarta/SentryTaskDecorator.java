package io.sentry.spring.jakarta;

import io.sentry.IHub;
import io.sentry.Sentry;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.Async;

/**
 * Sets a current hub on a thread running a {@link Runnable} given by parameter. Used to propagate
 * the current {@link IHub} on the thread executing async task - like MVC controller methods
 * returning a {@link Callable} or Spring beans methods annotated with {@link Async}.
 */
public final class SentryTaskDecorator implements TaskDecorator {
  @Override
  public @NotNull Runnable decorate(final @NotNull Runnable runnable) {
    final IHub oldState = Sentry.getCurrentHub();
    final IHub newHub = Sentry.getCurrentHub().clone();
    return () -> {
      Sentry.setCurrentHub(newHub);
      try {
        runnable.run();
      } finally {
        Sentry.setCurrentHub(oldState);
      }
    };
  }
}
