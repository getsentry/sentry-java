package io.sentry.android;

import io.sentry.ISerializer;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEvent;

public class AndroidSerializer implements ISerializer {
  @Override
  public SentryEnvelope deserializeEnvelope(String envelope) {
    return null;
  }

  @Override
  public String serialize(SentryEvent event) {
    return null;
  }
}
