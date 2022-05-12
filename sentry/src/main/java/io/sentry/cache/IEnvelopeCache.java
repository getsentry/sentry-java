package io.sentry.cache;

import io.sentry.SentryEnvelope;
import io.sentry.hints.Hints;
import org.jetbrains.annotations.NotNull;

public interface IEnvelopeCache extends Iterable<SentryEnvelope> {

  void store(@NotNull SentryEnvelope envelope, @NotNull Hints hints);

  default void store(@NotNull SentryEnvelope envelope) {
    store(envelope, new Hints());
  }

  void discard(@NotNull SentryEnvelope envelope);
}
