package io.sentry.cache;

import io.sentry.Hint;
import io.sentry.SentryEnvelope;
import org.jetbrains.annotations.NotNull;

public interface IEnvelopeCache extends Iterable<SentryEnvelope> {

  @Deprecated
  void store(@NotNull SentryEnvelope envelope, @NotNull Hint hint);

  default boolean storeEnvelope(@NotNull SentryEnvelope envelope, @NotNull Hint hint) {
    store(envelope, hint);
    return true;
  }

  @Deprecated
  default void store(@NotNull SentryEnvelope envelope) {
    storeEnvelope(envelope, new Hint());
  }

  void discard(@NotNull SentryEnvelope envelope);
}
