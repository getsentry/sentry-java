package io.sentry;

public interface ISerializer {
  SentryEnvelope deserializeEnvelope(String envelope);

  String serialize(SentryEvent event);
}
