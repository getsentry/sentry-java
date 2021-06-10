package io.sentry.reactor;

import io.sentry.IHub;
import io.sentry.Sentry;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

public final class SentryScheduleHook implements Function<Runnable, Runnable> {
  @Override
  public Runnable apply(final @NotNull Runnable runnable) {
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
