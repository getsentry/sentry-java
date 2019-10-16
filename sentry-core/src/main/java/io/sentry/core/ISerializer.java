package io.sentry.core;

import java.io.IOException;
import java.io.Writer;

public interface ISerializer {
  SentryEnvelope deserializeEnvelope(String envelope);

  SentryEvent deserializeEvent(String envelope);

  void serialize(SentryEvent event, Writer writer) throws IOException;
}
