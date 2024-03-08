package io.sentry.opentelemetry;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;
import io.sentry.IHub;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

public final class SentryContextStorage implements ContextStorage {

  public static final ContextKey<IHub> HUB_KEY = ContextKey.named("sentry.hub");
  private final @NotNull Logger logger = Logger.getLogger(SentryContextStorage.class.getName());

  private final @NotNull ContextStorage contextStorage;

  public SentryContextStorage(final @NotNull ContextStorage contextStorage) {
    this.contextStorage = contextStorage;
    logger.log(Level.SEVERE, "SentryContextStorage ctor called");
  }

  @Override
  public Scope attach(Context toAttach) {
    // TODO do we need to fork here as well?
    // scenario: Context is propagated from thread A to thread B without changes
    // OTEL likely also dosn't fork in that case so we probably also don't have to
    // or maybe shouldn't even to better align with OTEL
    // but since OTEL Context is immutable it doesn't have the same consequence for OTEL as for us

    // incoming non sentry wrapped context that already has scopes in it
    if (toAttach instanceof SentryContextWrapper) {
      return contextStorage.attach(toAttach);
    } else {
      return contextStorage.attach(SentryContextWrapper.wrap(toAttach));
    }
  }

  @Override
  public Context current() {
    return contextStorage.current();
  }
}
