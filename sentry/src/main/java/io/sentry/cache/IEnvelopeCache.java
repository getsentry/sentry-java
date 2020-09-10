package io.sentry.cache;

import io.sentry.SentryEnvelope;
import org.jetbrains.annotations.Nullable;

public interface IEnvelopeCache extends Iterable<SentryEnvelope> {

  void store(SentryEnvelope envelope, @Nullable Object hint);

  default void store(SentryEnvelope envelope) {
    store(envelope, null);
  }

  void discard(SentryEnvelope envelope);
}
