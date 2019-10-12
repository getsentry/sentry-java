package io.sentry.core;

public interface ISerializer {
  SentryEnvelope deserializeEnvelope(String envelope);

  SentryEvent deserializeEvent(String envelope);

  String serialize(SentryEvent event);
}
