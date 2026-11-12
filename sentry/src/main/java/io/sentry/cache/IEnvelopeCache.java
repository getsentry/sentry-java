package io.sentry.cache;

import io.sentry.Hint;
import io.sentry.SentryEnvelope;
import org.jetbrains.annotations.NotNull;

public interface IEnvelopeCache extends Iterable<SentryEnvelope> {

  /**
   * Stores an envelope.
   *
   * @return whether the envelope was stored
   */
  boolean storeEnvelope(@NotNull SentryEnvelope envelope, @NotNull Hint hint);

  void discard(@NotNull SentryEnvelope envelope);
}
