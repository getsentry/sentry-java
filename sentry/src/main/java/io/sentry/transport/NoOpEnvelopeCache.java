package io.sentry.transport;

import io.sentry.SentryEnvelope;
import io.sentry.cache.IEnvelopeCache;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpEnvelopeCache implements IEnvelopeCache {
  private static final NoOpEnvelopeCache instance = new NoOpEnvelopeCache();

  public static NoOpEnvelopeCache getInstance() {
    return instance;
  }

  @Override
  public void store(@NotNull SentryEnvelope envelope, @Nullable Map<String, Object> hint) {}

  @Override
  public void discard(@NotNull SentryEnvelope envelope) {}

  @NotNull
  @Override
  public Iterator<SentryEnvelope> iterator() {
    return new ArrayList<SentryEnvelope>(0).iterator();
  }
}
