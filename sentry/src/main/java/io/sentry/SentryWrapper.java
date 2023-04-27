package io.sentry;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public final class SentryWrapper {
  public static <U> Callable<U> wrapCallable(Callable<U> callable) {
    final IHub oldState = Sentry.getCurrentHub();
    final IHub newHub = Sentry.getCurrentHub().clone();

    return () -> {
      Sentry.setCurrentHub(newHub);
      try {
        return callable.call();
      } finally {
        Sentry.setCurrentHub(oldState);
      }
    };
  }

  public static <U> Supplier<U> wrapSupplier(Supplier<U> supplier) {
    final IHub oldState = Sentry.getCurrentHub();
    final IHub newHub = Sentry.getCurrentHub().clone();

    return () -> {
      Sentry.setCurrentHub(newHub);
      try {
        return supplier.get();
      } finally {
        Sentry.setCurrentHub(oldState);
      }
    };
  }
}
