package io.sentry.core.cache;

import io.sentry.core.SentryEnvelope;
import org.jetbrains.annotations.Nullable;

public interface ISessionCache extends Iterable<SentryEnvelope> {

  void store(SentryEnvelope envelope, @Nullable Object hint);

  default void store(SentryEnvelope envelope) {
    store(envelope, null);
  }

  void discard(SentryEnvelope envelope);
}
