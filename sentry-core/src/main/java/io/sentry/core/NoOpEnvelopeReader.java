package io.sentry.core;

import java.io.IOException;
import java.io.InputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpEnvelopeReader implements IEnvelopeReader {

  private static final NoOpEnvelopeReader instance = new NoOpEnvelopeReader();

  private NoOpEnvelopeReader() {}

  public static NoOpEnvelopeReader getInstance() {
    return instance;
  }

  @Override
  public @Nullable SentryEnvelope read(@NotNull InputStream stream) throws IOException {
    return null;
  }
}
