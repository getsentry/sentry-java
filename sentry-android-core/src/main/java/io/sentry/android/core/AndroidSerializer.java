package io.sentry.android.core;

import io.sentry.core.ISerializer;
import io.sentry.core.SentryEnvelope;
import io.sentry.core.SentryEvent;

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
