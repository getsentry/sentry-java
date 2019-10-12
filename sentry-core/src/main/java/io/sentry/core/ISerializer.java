package io.sentry.core;

public interface ISerializer {
  SentryEnvelope deserializeEnvelope(String envelope);

  String serialize(SentryEvent event);
}
