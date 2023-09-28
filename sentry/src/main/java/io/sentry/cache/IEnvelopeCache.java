package io.sentry.cache;

import io.sentry.Hint;
import io.sentry.SentryEnvelope;
import org.jetbrains.annotations.NotNull;

public interface IEnvelopeCache extends Iterable<SentryEnvelope> {

  void store(@NotNull SentryEnvelope envelope, @NotNull Hint hint);

  default void store(@NotNull SentryEnvelope envelope) {
    store(envelope, new Hint());
  }

  void discard(@NotNull SentryEnvelope envelope);
}
