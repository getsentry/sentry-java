package io.sentry.spring.webflux;

import io.sentry.IHub;
import io.sentry.Sentry;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Signal;

@ApiStatus.Experimental
public final class SentryReactor {

  /**
   * Takes the Sentry {@link IHub} associated with the HTTP request and sets it on current thread
   * for the time of executing {@link Consumer} given by parameter.
   *
   * @param consumer - the consumer to execute
   * @param <T> type of signal
   * @return a consumer of signal
   */
  public static <T> @NotNull Consumer<Signal<T>> withSentry(final @NotNull Consumer<T> consumer) {
    return signal -> {
      if (!signal.isOnNext()) return;
      final IHub currentHub = Sentry.getCurrentHub();
      final IHub contextViewHub =
          signal.getContextView().getOrDefault(SentryWebFilter.HUB_REACTOR_CONTEXT_ATTRIBUTE, null);
      if (contextViewHub != null) {
        Sentry.setCurrentHub(contextViewHub);
        try {
          consumer.accept(signal.get());
        } finally {
          Sentry.setCurrentHub(currentHub);
        }
      } else {
        consumer.accept(signal.get());
      }
    };
  }
}
