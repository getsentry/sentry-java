package io.sentry.cache;

import io.sentry.SentryEnvelope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IEnvelopeCache extends Iterable<SentryEnvelope> {

  void store(@NotNull SentryEnvelope envelope, @Nullable Object hint);

  default void store(@NotNull SentryEnvelope envelope) {
    store(envelope, null);
  }

  void discard(@NotNull SentryEnvelope envelope);
}
